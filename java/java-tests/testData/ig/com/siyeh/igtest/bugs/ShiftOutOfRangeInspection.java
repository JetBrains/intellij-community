package com.siyeh.igtest.bugs;

public class ShiftOutOfRangeInspection {
    private static final int THIRTYTWO = 32;
    private static final int SIXTYFOUR = 64;

    public void foo()
    {
        int x = 4;
        int y = x << -1;
        int i = x >> -1;
        int z = x >> 30;
        int w = x >> THIRTYTWO;
    }
    public void fooShort()
    {
        short x = 4;
        int y = x << -1;
        int i = x >> -1;
        int z = x >> 30;
        int w = x >> THIRTYTWO;
    }
    public void fooLong()
    {
        long x = 4;
        long y = x << -1;
        long i = x >>> -1;
        long z = x >> 60;
        long w = x >> SIXTYFOUR;
    }
}
