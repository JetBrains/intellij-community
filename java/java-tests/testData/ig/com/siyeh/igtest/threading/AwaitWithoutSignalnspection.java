package com.siyeh.igtest.threading;

import java.util.concurrent.locks.Condition;

public class AwaitWithoutSignalnspection {
    private final Condition x = null;

    public void foo() throws InterruptedException {
        x.await();
    }
}
