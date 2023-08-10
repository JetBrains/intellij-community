package com.siyeh.igtest.performance;

import java.io.IOException;

public class BooleanConstructorInspection
{
    public BooleanConstructorInspection()
    {
    }

    public void foo() throws IOException
    {
        String x = "true";
        final Boolean b0 = new Boolean(true && false);
        final Boolean b1 = new Boolean(true);
        final Boolean b2 = new Boolean(false);
        final Boolean b3 = new Boolean("true");
        final Boolean b4 = new Boolean("false");
        final Boolean b5 = new Boolean(x);
    }
}