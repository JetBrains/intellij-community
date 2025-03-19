package com.siyeh.igtest.exceptionHandling;

public class CatchGenericClassInspection
{
    public void foo() throws Throwable
    {
        if(bar())
        {
            try
            {
            }
            catch(Exception e)
            {
                System.out.println("foo");
            }
        }
        if(bar())
        {
            try
            {
            }
            catch(RuntimeException e)
            {
                System.out.println("foo");
            }
        }
        if(bar())
        {
            try
            {
            }
            catch(Throwable e)
            {
                System.out.println("foo");
            }
        }
        if(bar())
        {
            try
            {
            }
            catch(Error e)
            {
                System.out.println("foo");
            }
        }
        if(bar())
        {
            try
            {
                throw new Exception();
            }
            catch(Exception e)
            {
                System.out.println("foo");
            }
        }
        if(bar())
        {
            try
            {
                throw new RuntimeException();
            }
            catch(RuntimeException e)
            {
                System.out.println("foo");
            }
        }
        if(bar())
        {
            try
            {
                throw new Throwable();
            }
            catch(Throwable e)
            {
                System.out.println("foo");
            }
        }
        if(bar())
        {
            try
            {
                throw new Error();
            }
            catch(Error e)
            {
                System.out.println("foo");
            }
        }
    }

    private boolean bar()
    {
        return true;
    }

}
