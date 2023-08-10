package com.siyeh.igtest.threading;

public  class NonSynchronizedMethodOverridesSynchronizedMethodInspection2
        extends NonSynchronizedMethodOverridesSynchronizedMethodInspection1
{
    public synchronized void fooBar()
    {

    }

    public void fooBang()
    {

    }

    public synchronized void fooBaz()
    {

    }

    public void fooBarangus()
    {

    }
}
