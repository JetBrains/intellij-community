package com.siyeh.igtest.threading;

public class NestedSynchroneInspection
{

    public void foo()
    {
        synchronized(this)
        {
            synchronized(this)
            {

            }
        }
    }
}
