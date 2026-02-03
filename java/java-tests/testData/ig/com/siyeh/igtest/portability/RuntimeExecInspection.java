package com.siyeh.igtest.portability;

import java.io.IOException;

public class RuntimeExecInspection
{
    public RuntimeExecInspection()
    {
    }

    public void foo() throws IOException
    {
        final Runtime runtime = Runtime.getRuntime();
        runtime.exec("foo");
    }
}