package com.siyeh.igtest.threading;

public class UnconditionalWaitInspection
{
    public void foo() throws InterruptedException {
        final Object bar = new Object();
        synchronized (this) {
            bar.toString();
            bar.wait();
        }
        synchronized (this) {
            if(foobar())
            {

            }
            bar.wait();
        }
        synchronized (this) {
            wait();
        }
    }

    private boolean foobar() {
        return false;
    }

    public synchronized void bar()
    {
        notifyAll();
    }
}
