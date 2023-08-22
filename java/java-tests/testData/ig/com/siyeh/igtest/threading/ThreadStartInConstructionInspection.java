package com.siyeh.igtest.threading;

public class ThreadStartInConstructionInspection
{
    private final Object lock = new Object();

    {
        new MyThread(new Runnable(){
            public void run() {
            }
        }).start();
    }
    public ThreadStartInConstructionInspection() {
        new Thread().start();
    }
}
