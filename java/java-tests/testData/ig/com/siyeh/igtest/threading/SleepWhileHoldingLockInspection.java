package com.siyeh.igtest.threading;

import java.util.concurrent.locks.Condition;

public class SleepWhileHoldingLockInspection {
    private Condition lock;

    public void foo() throws InterruptedException {
        synchronized (this)
                {
                    Thread.sleep(1000);
                }
    }

    public synchronized void bar() throws InterruptedException {
        Thread.sleep(1000);
    }
}
