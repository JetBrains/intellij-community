package com.siyeh.igtest.threading;

public class WaitWithoutNotifyInspection {
    private final Object x = new Object();

    public void foo() throws InterruptedException {
        x.wait();
        x.wait(100L);
    }
}

