package com.siyeh.igtest.threading;

public class SynchronizeOnThis
{
    private Object m_lock = new Object();

    public void fooBar() throws InterruptedException {
        synchronized ((<warning descr="Lock operations on 'this' may have unforeseen side-effects">this</warning>))
        {
            (this).<warning descr="Lock operations on 'this' may have unforeseen side-effects">wait</warning>();
            this.<warning descr="Lock operations on 'this' may have unforeseen side-effects">notify</warning>();
            this.<warning descr="Lock operations on 'this' may have unforeseen side-effects">notifyAll</warning>();
            <warning descr="Lock operations on 'this' may have unforeseen side-effects">wait</warning>(1000L);
            <warning descr="Lock operations on 'this' may have unforeseen side-effects">notify</warning>();
            <warning descr="Lock operations on 'this' may have unforeseen side-effects">notifyAll</warning>();
            System.out.println("");
        }

        synchronized (<warning descr="Lock operations on a class may have unforeseen side-effects">SynchronizeOnThis.class</warning>) {
            this.getClass().<warning descr="Lock operations on a class may have unforeseen side-effects">wait</warning>();
        }
        Class x = SynchronizeOnThis.class;
        synchronized (<warning descr="Lock operations on a class may have unforeseen side-effects">x</warning>) {}
    }

    private class X {
        void a() {
            synchronized (X.class) {
                System.out.println();
            }

            synchronized (getClass()) {}
        }
    }
}
