package com.siyeh.igtest.threading;

public class SynchronizeOnNonFinalFieldInspection
{
    private Object m_lock = new Object();

    public void fooBar()
    {
        synchronized(m_lock)
        {
             System.out.println("");
        }
    }
}
