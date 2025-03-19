package com.siyeh.igtest.exceptionHandling;

public class ExceptionWrappedInspection
{
    public void foo() throws Exception
    {
        try
        {
            System.out.println("foo");
        }
        catch(Error e)
        {
            throw new Exception();
        }

        try
        {
            System.out.println("foo");
        }
        catch(Error e)
        {
            e.printStackTrace();
            throw e;
        }

        try
        {
            System.out.println("foo");
        }
        catch(OutOfMemoryError e)
        {
            e.printStackTrace();
            throw e;
        }
        try
        {
            System.out.println("foo");
        }
        catch(OutOfMemoryError e)
        {
            throw new IllegalAccessError();
        }
    }
}
