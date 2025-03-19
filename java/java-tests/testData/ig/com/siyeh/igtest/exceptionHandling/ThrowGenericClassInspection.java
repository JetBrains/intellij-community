package com.siyeh.igtest.exceptionHandling;

public class ThrowGenericClassInspection
{
    public void foo() throws Throwable
    {
        if(bar())
        {
            throw new Exception();
        }
        if(bar())
        {
            throw new RuntimeException();
        }
        if(bar())
        {
            throw new Throwable();
        }
        if(bar())
        {
            throw new Error();
        }
    }

    private boolean bar()
    {
        return true;
    }

}
