package com.siyeh.igtest.bugs.var_arg;

import java.lang.invoke.MethodHandle;

public class PrimitiveArrayArgumentToVariableArgMethod
{
    PrimitiveArrayArgumentToVariableArgMethod(Object... os) {}

    public static void main(String[] arg) throws Throwable
    {
        methodVarArgObject(<warning descr="Confusing primitive array argument to varargs method">new byte[3]</warning>);
        methodVarArgByteArray(new byte[3]);
        MethodHandle meh = null;
        meh.invokeExact(new int[] { });
    }

    private static void methodVarArgObject(Object... bytes)
    {
    }

    private static void methodVarArgByteArray(byte[]... bytes)
    {
    }

    class X<T> {
        void method(T... t) {
        }
    }

    void foo(byte[] bs) {
        final X<byte[]> x = new X<byte[]>();
        x.method(bs);
      final X<Object> y = new X<Object>();
      y.method(<warning descr="Confusing primitive array argument to varargs method">bs</warning>);
    }

    void m() {
        String.format("%s", <warning descr="Confusing primitive array argument to varargs method">new int[]{1, 2, 3}</warning>);
    }

    static void bar1(java.lang.Object... objects) {
        for (java.lang.Object object : objects) {
            System.out.println("object: " + object);
        }
    }

    static void bar2(int... ints) {
        for (int anInt : ints) {
            System.out.print(anInt);
        }
    }

    public static void invoke() {
        int[] ints = {1, 2, 3};
        bar1(<warning descr="Confusing primitive array argument to varargs method">ints</warning>); // warn here
        bar2(ints); // no warning needed here

        new PrimitiveArrayArgumentToVariableArgMethod(<warning descr="Confusing primitive array argument to varargs method">ints</warning>);
    }

    enum E {
        A(<warning descr="Confusing primitive array argument to varargs method">new int[] {}</warning>);

        E(Object... os) {}
    }
}