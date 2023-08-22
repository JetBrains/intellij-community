package com.siyeh.igtest.exceptionHandling;

public class EmptyFinallyBlockInspection
{
    public void foo()
    {
        try
        {

        }
        finally
        {
        }
    }

    public int bar()
    {
        try
        {
            return 4;
        }
        finally
        {
        }
    }
}
