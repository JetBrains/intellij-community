package com.siyeh.igtest.threading;

public  class NonSynchronizedMethodOverridesSynchronizedMethodInspection1
{
    public synchronized void fooBar()
    {

    }

    public synchronized void fooBang()
    {

    }

    public void fooBaz()
    {

    }

    public void fooBarangus()
    {

    }
}
