package com.siyeh.igtest.performance;

import java.io.IOException;

public class StringConstructorInspection
{
    public StringConstructorInspection()
    {
    }

    public void foo() throws IOException
    {
        new String("true");
    }
}