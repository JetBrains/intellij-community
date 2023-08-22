package com.siyeh.igtest.exceptionHandling;

public class UnusedCatchParameterInspectionInspection
{
    public void foo()
    {
        try
        {
            throw new Exception();
        }
        catch(Exception e)
        {
        }
        try
        {
            throw new Exception();
        }
        catch(Exception ignore)
        {
        }
        try
        {
            throw new Exception();
        }
        catch(Exception e)
        {
            //catch comment
        }
    }
}
