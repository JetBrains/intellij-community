package com.siyeh.igtest.threading;

import java.util.concurrent.locks.Condition;

public class WaitCalledOnConditionInspection
{
    private Condition lock;

    public  void foo() throws InterruptedException {
        lock.wait();
    }
}
