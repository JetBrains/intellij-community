package com.siyeh.igtest.j2me;

public class CheckForOutOfMemoryOnLargeArrayAllocationInspection{
    public void foo()
    {
        int[][][][][][][] ints = new int[2][2][2][2][2][2][2];
        int[][][][][] ints2 = new int[2][2][2][2][2];
        int[][][][][] ints3 = new int[2][2][2][32][];
        try {
            int[][][][][] ints4 = new int[2][2][2][32][];
        } catch (OutOfMemoryError e) {
        }
    }
}
