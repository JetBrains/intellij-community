package com.siyeh.igtest.performance;

public class SystemGCInspection
{
    public SystemGCInspection()
    {
    }

    public void foo()
    {
        System.gc();
        Runtime.getRuntime().gc();
    }
}