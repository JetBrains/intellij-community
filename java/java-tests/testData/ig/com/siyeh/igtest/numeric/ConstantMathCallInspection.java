package com.siyeh.igtest.numeric;

public class ConstantMathCallInspection{
    public void foo()
    {
        final double v = Math.sin(0.0);
        final double v1 = Math.asin(1.0);
        final long round = Math.round(2.5);
        long belowMin = Math.abs((long) Integer.MIN_VALUE + 1L);
    }
}
