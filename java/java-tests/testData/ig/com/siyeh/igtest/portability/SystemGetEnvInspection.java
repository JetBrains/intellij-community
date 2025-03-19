package com.siyeh.igtest.portability;

import java.io.IOException;

public class SystemGetEnvInspection
{
    public SystemGetEnvInspection()
    {
    }

    public void foo() throws IOException
    {
        System.getenv("foo");
    }
}