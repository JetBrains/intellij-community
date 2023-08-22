package com.siyeh.igtest.threading;

public class ArithmeticOnVolatileFieldInspection {
    private volatile int foo;
    private volatile double bar;

    public void add()
    {
        System.out.println(foo+bar);
    }
}
