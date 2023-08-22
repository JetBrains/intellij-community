package com.siyeh.igtest.bitwise.pointless_bitwise_expression;

import java.util.*;

public class PointlessBitwiseExpression {
    private static final int ZERO = 0;

    public static void main(String[] args) {
        final int i = 1;
        int j = <warning descr="'i & 0' can be replaced with '0'">i & 0</warning>;
        System.out.println(j);
        j = 3;
        int k = <warning descr="'j | 0' can be replaced with 'j'">j | 0</warning>;
        System.out.println(k);
        k = <warning descr="'j ^ 0' can be replaced with 'j'">j ^ 0</warning>;
        System.out.println(k);
        k = <warning descr="'j << 0' can be replaced with 'j'">j << 0</warning>;
        System.out.println(k);
        k = <warning descr="'j >> 0' can be replaced with 'j'">j >> 0</warning>;
        System.out.println(k);
        k = <warning descr="'j >>> 0' can be replaced with 'j'">j >>> 0</warning>;
        System.out.println(k);
        k = <warning descr="'j >>> ZERO' can be replaced with 'j'">j >>> ZERO</warning>;
        System.out.println(k);
    }

    public static void main3(String[] args) {
        final int i = 1;
        int j = <warning descr="'0 & i' can be replaced with '0'">0 & i</warning>;
        System.out.println(j);
        j = 3;
        int k = <warning descr="'0 | j' can be replaced with 'j'">0 | j</warning>;
        System.out.println(k);
        k = <warning descr="'0 ^ j' can be replaced with 'j'">0 ^ j</warning>;
        System.out.println(k);

    }

    public static void main2(String[] args) {
        final int i = 1;
        int j = <warning descr="'i & 0xffffffff' can be replaced with 'i'">i & 0xffffffff</warning>;
        System.out.println(j);
        j = 3;
        int k = <warning descr="'j | 0xffffffff' can be replaced with '0xffffffff'">j | 0xffffffff</warning>;
        System.out.println(k);
        j = 6;
        k = <warning descr="'j ^ 0xffffffff' can be replaced with '~j'">j ^ 0xffffffff</warning>;
        System.out.println(k);


    }

    public static void main4(String[] args) {
        final int i = 1;
        int j = <warning descr="'0xffffffff & i' can be replaced with 'i'">0xffffffff & i</warning>;
        System.out.println(j);
        j = 3;
        int k = <warning descr="'0xffffffff | j' can be replaced with '0xffffffff'">0xffffffff | j</warning>;
        System.out.println(k);
        j = 6;
        k = <warning descr="'0xffffffff ^ j' can be replaced with '~j'">0xffffffff ^ j</warning>;
        System.out.println(k);
    }

    public static void main5(String[] args) {
        Random in = new Random();
        System.out.println(in.nextInt() & in.nextInt());
    }

    void longExpressions(int i, int j) {
        i = <warning descr="'j & 0 & 100' can be replaced with '0'">j & 0 & 100</warning>;
    }

    void m(int i) {
      int h  = <warning descr="'0 << 8' can be replaced with '0'">0 << 8</warning>;
      int b = <warning descr="'i ^ i' can be replaced with '0'">i ^  i</warning>; // 0
      int c = <warning descr="'i & i' can be replaced with 'i'">i &  i</warning>; // i
      int d = <warning descr="'i | i' can be replaced with 'i'">i |  i</warning>; // i
    }

    void testChar(int value) {
        int res = value & '\uFFFF';
    }

    void testTilde(int x, long y) {
        int r1 = <warning descr="'x & ~x' can be replaced with '0'">x & ~x</warning>;
        int r2 = <warning descr="'~x & x' can be replaced with '0'">~x & x</warning>;
        int r3 = <warning descr="'1 & x & ~x' can be replaced with '1 & 0'">1 & x & ~x</warning>;
        int r4 = <warning descr="'x & ~x & 1' can be replaced with '0 & 1'">x & ~x & 1</warning>;
        long r5 = <warning descr="'y & (~(y))' can be replaced with '0L'">y & (~(y))</warning>;
        long r6 = <warning descr="'~y & (y)' can be replaced with '0L'">~y & (y)</warning>;
        long r7 = <warning descr="'~y & ~y' can be replaced with '~y'">~y & ~y</warning>;
        int r8 = <warning descr="'x | ~x' can be replaced with '-1'">x | ~x</warning>;
        long r9 = <warning descr="'y ^ ~y' can be replaced with '-1L'">y ^ ~y</warning>;
    }

    void testDoubleTilde(int x, long y) {
        int r1 = <warning descr="'~~x' can be replaced with 'x'">~~x</warning>;
        long r2 = ~<warning descr="'~~y' can be replaced with 'y'">~~y</warning>;
        int r3 = <warning descr="'~(~(x))' can be replaced with '(x)'">~(~(x))</warning>;
        long r4 = ~(~(<warning descr="'~(~y)' can be replaced with 'y'">~(~y)</warning>));
    }

    void testComments(int length) {
      final int bits = <warning descr="'0 // comment | length// 1 | 3' can be replaced with 'length// 1
                       | 3'"><caret>0 // comment
                       | length// 1
                       | 3</warning>;
    }
}
