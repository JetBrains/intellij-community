package com.siyeh.igtest.classlayout;

public class AnonymousInnerClassInspection {
    public void foo()
    {
        final Runnable runnable = new Runnable(){
                    public void run() {
                    }
                };
        runnable.run();
    }
}
