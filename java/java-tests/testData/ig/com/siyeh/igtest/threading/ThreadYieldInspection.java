package com.siyeh.igtest.threading;

public class ThreadYieldInspection
{
    public void foo()
    {
        Thread.yield();
    }
}
