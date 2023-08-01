package com.siyeh.igtest.exceptionHandling;

public class MyCheckedException extends Exception
{
    
    public MyCheckedException()
    {
    }

    public MyCheckedException(String message)
    {
        super(message);
    }

    public MyCheckedException(String message, Throwable cause)
    {
        super(message, cause);
    }

    public MyCheckedException(Throwable cause)
    {
        super(cause);
    }
}
