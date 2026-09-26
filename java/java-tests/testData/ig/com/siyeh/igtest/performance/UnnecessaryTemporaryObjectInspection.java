package com.siyeh.igtest.performance;

import java.io.IOException;

public class UnnecessaryTemporaryObjectInspection
{
    public UnnecessaryTemporaryObjectInspection()
    {
    }

    public void foo() throws IOException
    {
        final String s = new Integer(3).toString();
        final String s2 = new Double(3).toString();
        final String s3 = new Short((short) 3).toString();
        final String s4 = new Long(3).toString();
        final String s5 = new Float(3).toString();
        final String s6 = new Boolean(true).toString();

        final int i = new Integer("3").intValue();
        final double d = new Double("3").doubleValue();
        final short sh = new Short("3").shortValue();
        final long lo = new Long("3").longValue();
        final float f = new Float("3").floatValue();
        final boolean b = new Boolean("3").booleanValue();
    }
}