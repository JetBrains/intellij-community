package com.siyeh.igtest.naming;

public class ExceptionWithBadName extends Exception
{
    public ExceptionWithBadName()
    {
    }

    public ExceptionWithBadName(String message)
    {
        super(message);
    }

    public ExceptionWithBadName(String message, Throwable cause)
    {
        super(message, cause);
    }

    public ExceptionWithBadName(Throwable cause)
    {
        super(cause);
    }
}
