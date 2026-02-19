package com.siyeh.igtest.portability;

import java.io.IOException;

public class SystemExitInspection
{
    public SystemExitInspection()
    {
    }

    public void foo() throws IOException
    {
        System.exit(0);
        Runtime.getRuntime().exit(0);
        Runtime.getRuntime().halt(0);
    }
}