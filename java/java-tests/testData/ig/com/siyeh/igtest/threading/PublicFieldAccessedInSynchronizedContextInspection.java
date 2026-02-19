package com.siyeh.igtest.threading;

public class PublicFieldAccessedInSynchronizedContextInspection
{
    public int foo = 0;
    protected int baz = 0;
    private int bar = 0;
    final int barangus = 0;

    public void bar()
    {
        synchronized(this)
        {
            foo++;
            bar++;
            baz++;
            System.out.println(barangus);
        }
    }

    public synchronized void bar2()
    {
        foo++;
        bar++;
        baz++;
        System.out.println(baz);
        System.out.println(barangus);
    }
}
