/*
 * Copyright (c) 2012, JInterval Project.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *   * Redistributions of source code must retain the above copyright notice,
 *     this list of conditions and the following disclaimer.
 *   * Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in the
 *     documentation and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF
 * THE POSSIBILITY OF SUCH DAMAGE.
 */
package net.java.jinterval.text2interval.fractions;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintStream;
import java.io.Serializable;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import net.java.jinterval.interval.set.SetInterval;
import net.java.jinterval.interval.set.SetIntervalOps;
import net.java.jinterval.rational.BinaryValueSet;
import net.java.jinterval.rational.Rational;
import net.java.jinterval.rational.RationalOps;

/**
 * An entry to the table for toDec algorithm
 */
public class ToDecTable implements Serializable {

    static final long serialVersionUID = 100;

    final BinaryValueSet f;
    final int qb;
    final BigInteger cblMin;
    final BigInteger cbrMax;
    final Rational vlMin;
    final Rational vrMax;
    final int r;

    private ToDecTable(BinaryValueSet f, int qb, int r) {
        this.f = f;
        this.qb = qb;
        int e = qb + f.getPrecision();
        assert f.getMinExp() <= e && e <= f.getMaxExp();
        this.cblMin = e == f.getMinExp()
                ? BigInteger.ONE
                : BigInteger.ONE.shiftLeft(f.getPrecision()).subtract(BigInteger.ONE);
        this.cbrMax = BigInteger.ONE.shiftLeft(f.getPrecision() + 1).add(BigInteger.ONE);
        vlMin = Rational.valueOf(cblMin, qb);
        vrMax = Rational.valueOf(cbrMax, qb);
        this.r = r;
        Rational ulp = Rational.exp2(qb + 1);
        Rational ulpD = r >= 0
                ? Rational.valueOf(BigInteger.TEN.pow(r))
                : RationalOps.recip(Rational.valueOf(BigInteger.TEN.pow(-r)));
        Rational ulpD1 = RationalOps.mul(ulpD, Rational.valueOf(10));
        assert ulpD.compareTo(ulp) <= 0;
        assert ulp.compareTo(ulpD1) < 0;
    }

    static List<ToDecTable> makeTable(BinaryValueSet f) {
        List<ToDecTable> table = new ArrayList<>();
        int qMin = f.getMinExp() - f.getPrecision() + 1;
        String s = BigInteger.ONE.shiftLeft(-qMin).toString();
        int r = -s.length();
        Rational ulpD = RationalOps.recip(Rational.valueOf(BigInteger.TEN.pow(-r)));
        Rational ulpD1 = RationalOps.mul(ulpD, Rational.valueOf(10));
        table.add(new ToDecTable(f, qMin - 1, r));
        for (int e = f.getMinExp() + 1; e <= f.getMaxExp(); e++) {
            int q = e - f.getPrecision() + 1;
            Rational ulp = Rational.exp2(q);
            while (ulp.compareTo(ulpD1) >= 0) {
                r++;
                ulpD = ulpD1;
                ulpD1 = RationalOps.mul(ulpD, Rational.valueOf(10));
            }
            table.add(new ToDecTable(f, q - 1, r));
        }
        return table;
    }

    static void printTable(BinaryValueSet f, List<ToDecTable> table) {
        System.out.println("prec=" + f.getPrecision());
        for (ToDecTable e : table) {
            System.out.println("qb=" + e.qb
                    + " v in [ " + e.vlMin + " .. " + e.vrMax + "]"
                    + " ulp=" + Rational.exp2(e.qb + 1)
                    + " r=" + e.r
            );
        }
    }

    public static SetInterval findNonzeroFract(BinaryValueSet f, List<ToDecTable> table) {
        int P = f.getPrecision();
        int qMin = f.getMinExp() - P + 1;
        int qMax = f.getMaxExp() - P + 1;
        System.out.println("prec=" + f.getPrecision()
                + " q in [" + qMin + ".." + qMax + "]"
                + " " + table.size() + " table entries");
        Rational epsMin = null, epsMax = null;
        for (ToDecTable entry : table) {
            System.out.println(entry.vlMin + ".." + entry.vrMax
                    + " qb=" + entry.qb
                    + " r=" + entry.r);
            BigInteger cblMin = entry.cblMin;
            BigInteger cbrMax = entry.cbrMax;
            int r = entry.r;
            Rational halfUlp = Rational.exp2(entry.qb);
            Rational ulpD = r >= 0
                    ? Rational.valueOf(BigInteger.TEN.pow(r))
                    : RationalOps.recip(Rational.valueOf(BigInteger.TEN.pow(-r)));
            Rational alpha = RationalOps.div(halfUlp, ulpD);
            alpha = RationalOps.mul(Rational.valueOf(2), alpha);
//            System.out.println("  alpha=" + alpha);
            Fractions fractions = new Fractions(alpha);
            BigInteger cbExact = fractions.searchExact(cblMin, cbrMax);
            if (cbExact != null) {
                Rational fH = RationalOps.mul(Rational.valueOf(cbExact), alpha);
                BigInteger sH = fH.toBigInteger();
                assert Rational.valueOf(sH).equals(fH);
                System.out.println("  exact cb=0x" + cbExact.toString(16) + " sH=" + sH + " fH=" + fH);
            }
            BigInteger cbBelow = fractions.search(cblMin, cbrMax, false);
            if (cbBelow != null) {
                Rational v = Rational.valueOf(cbBelow, entry.qb);
                Rational fH = RationalOps.mul(Rational.valueOf(cbBelow), alpha);
                BigInteger sH = fH.toBigInteger();
                Rational eps = RationalOps.sub(fH, Rational.valueOf(sH));
                System.out.println("  below cb=0x" + cbBelow.toString(16) + " eps=" + Double.toHexString(eps.doubleValue()));
                if (eps.signum() > 0 && (epsMin == null || eps.compareTo(epsMin) < 0)) {
                    epsMin = eps;
                }
            }
            BigInteger cbAbove = fractions.search(cblMin, cbrMax, true);
            if (cbAbove != null) {
                Rational v = Rational.valueOf(cbAbove, entry.qb);
                Rational fH = RationalOps.mul(Rational.valueOf(cbAbove), alpha);
                BigInteger sH = fH.toBigInteger();
                Rational eps = RationalOps.sub(fH, Rational.valueOf(sH));
                Rational eps1 = RationalOps.sub(Rational.one(), eps);
                if (eps1.signum() > 0 && (epsMax == null || eps.compareTo(epsMax) > 0)) {
                    epsMax = eps;
                }
                System.out.println("  above cb=0x" + cbAbove.toString(16) + " eps=1-" + Double.toHexString(eps.doubleValue()));
            }
        }
        return SetIntervalOps.nums2(epsMin, epsMax);
    }

    public static void exploreTable(BinaryValueSet f) {
        List<ToDecTable> table = makeTable(f);
//        printTable(f, table);
        SetInterval epsInterval = findNonzeroFract(f, table);
        Rational epsMin = (Rational) epsInterval.inf();
        Rational epsMax = (Rational) epsInterval.sup();
        Rational epsMax1 = RationalOps.sub(Rational.one(), epsMax);
        Rational eps = RationalOps.min(epsMin, epsMax1);
        int M = -eps.intFloorLog2();
        System.out.println("eps in [ " + Double.toHexString(epsMin.doubleValue())
                + " .. 1 - " + Double.toHexString(epsMax1.doubleValue()) + " ]"
                + " M=" + M);
        int rMin = table.get(0).r;
        int rMax = table.get(0).r;

    }

    private static void writeTable(BinaryValueSet f, File file) throws IOException {
        List<ToDecTable> table = makeTable(f);
        try (ObjectOutputStream out
                = new ObjectOutputStream(
                        new BufferedOutputStream(
                                new FileOutputStream(file)))) {
            out.writeObject(table);
        }
    }

    private static List<ToDecTable> readTable(File file) throws IOException, ClassNotFoundException {
        try (ObjectInputStream in
                = new ObjectInputStream(
                        new BufferedInputStream(
                                new FileInputStream(file)))) {
            return (List<ToDecTable>) in.readObject();
        }
    }
    
    private static void genTableR(PrintStream out, List<ToDecTable> table) {
        out.println("static int[] tableR = {");
        for (ToDecTable entry: table) {
            out.println("  " + entry.r + ", // qb=" + entry.qb);
        }
        out.println("};");
        out.println();
        int pMin = -table.get(table.size() - 1).r;
        int pMax = -table.get(0).r;
        out.println("static final int MIN_POW_5 = " + pMin + ";");
        out.println();
        Rational pow5 = RationalOps.recip(Rational.valueOf(BigInteger.valueOf(5).pow(-pMin)));
        out.println("");
        out.println("private static p(int q, long ch, long cl) {}");
        out.println();
        for (int p = pMin; p <= pMax; p++) {
            int q = pow5.intFloorLog2() - 127;
            BigInteger c = RationalOps.div(pow5, Rational.exp2(q)).toBigInteger();
            if (p != 0) {
                c = c.add(BigInteger.ONE);
            }
            long cl = c.longValue();
            long ch = c.shiftRight(64).longValue();
            out.printf("  p(%d, 0x%016xL, 0x%016xL); // 5^%d\n", q, ch, cl, p);
            pow5 = RationalOps.mul(pow5, Rational.valueOf(5));
        }
    }

    public static void main(String[] args) throws IOException, ClassNotFoundException {
        BinaryValueSet f = BinaryValueSet.BINARY64;
        File file = new File("double-table,ser");
        writeTable(f, file);
        List<ToDecTable> table = readTable(file);
        genTableR(System.out, table);
    }
}