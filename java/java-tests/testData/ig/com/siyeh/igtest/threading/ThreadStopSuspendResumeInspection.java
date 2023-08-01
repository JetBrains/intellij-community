package com.siyeh.igtest.threading;

public class ThreadStopSuspendResumeInspection
{
    public void foo()
    {
        Thread.currentThread().stop();
        Thread.currentThread().stop(null);
        Thread.currentThread().resume();
        Thread.currentThread().suspend();
    }
}
