package com.siyeh.igtest.performance;

import java.io.IOException;
import java.util.*;

public class MultiplyOrDivideByPowerOfTwoInspection
{
    public MultiplyOrDivideByPowerOfTwoInspection()
    {
    }

    public void foo() throws IOException
    {
        final int i = 3 + 3*8;
        final int j = i / 8;
        final int k = j / 7;
        final int m = j * 7;
        System.out.println("i = " + i);
        System.out.println("j = " + j);
        System.out.println("k = " + k);
        System.out.println("m = " + m);
    }
}