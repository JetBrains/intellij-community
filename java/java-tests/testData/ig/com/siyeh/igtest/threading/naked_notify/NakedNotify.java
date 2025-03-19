package com.siyeh.igtest.threading;

public class NakedNotify
{
    public void foo()
    {
        final Object bar = new Object();
        synchronized (this) {
            bar.toString();
            bar.notify();
        }
        synchronized (this) {
            <warning descr="Call to 'notifyAll()' without corresponding state change">notifyAll</warning>();
        }
        synchronized (this) {
            <warning descr="Call to 'notify()' without corresponding state change">notify</warning>();
        }
    }

    public synchronized void bar()
    {
        <warning descr="Call to 'notifyAll()' without corresponding state change">notifyAll</warning>();
    }

    private boolean flag = false;
    public synchronized void test() {
        flag = true;
        notify();
    }
}
