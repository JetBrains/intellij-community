package com.siyeh.igtest.performance;

public class ZeroLengthArrayInitializationInspection
{
    public static final int[] EMPTY_INT_ARRAY = new int[0];

    public void getTabName()
    {
        final int[] ints = new int[0];
    }
}
