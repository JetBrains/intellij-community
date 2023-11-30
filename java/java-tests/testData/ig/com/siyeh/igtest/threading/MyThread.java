package com.siyeh.igtest.threading;

public class MyThread extends Thread
{
    public MyThread(Runnable runnable)
    {
        super(runnable);
    }
}
