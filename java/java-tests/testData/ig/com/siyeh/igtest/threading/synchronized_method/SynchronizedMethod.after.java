package com.siyeh.igtest.threading.synchronized_method;

public class SynchronizedMethod {
    public void fooBar() {
        synchronized (this) {
            System.out.println("foo");
        }
    }

    public static void bar() {
        synchronized (SynchronizedMethod.class) {
            final var foo = "foo";
            System.out.println(foo);
        }
    }

    public synchronized native void fooBaz();

    static class X extends SynchronizedMethod {

        @Override
        public synchronized void fooBar() {
            super.fooBar();
        }
    }
}
