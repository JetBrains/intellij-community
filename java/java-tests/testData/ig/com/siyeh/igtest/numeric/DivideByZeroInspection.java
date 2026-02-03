package com.siyeh.igtest.numeric;

public class DivideByZeroInspection {
    private static final double Z = 0.0;

    public void foo()
    {
        double x = Math.sin(3.0);
        final double v = x / 0.0;
        final double y = x / Z;
        final double y2 = x % Z;

         double y3 = 4.0;
         y3 %= 0.0;
         y3 /= 0.0;
    }
}
