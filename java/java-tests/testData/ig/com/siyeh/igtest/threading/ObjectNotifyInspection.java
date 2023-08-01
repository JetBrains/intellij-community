package com.siyeh.igtest.threading;

import java.util.concurrent.locks.Condition;

public class ObjectNotifyInspection
{
    public void foo()
    {
        final Object bar = new Object();
        bar.notify();
        notify();
        Condition condition = null;
        condition.signal();
        condition.notify();
        condition.notifyAll();
    }
}
