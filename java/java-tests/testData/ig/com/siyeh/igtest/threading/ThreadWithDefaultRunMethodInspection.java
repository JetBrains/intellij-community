package com.siyeh.igtest.threading;

public class ThreadWithDefaultRunMethodInspection {
    public void foo()
    {
        Thread thread = new Thread();
        new Thread("foo");
        ThreadGroup threadGroup = new ThreadGroup("foo");
        new Thread(threadGroup, "bar");
        new Thread(threadGroup, new Runnable(){
            public void run() {
            }
        });      
        new Thread("foo"){};
        new Thread("foo"){
            public void run() {
            }
        };

    }
}
