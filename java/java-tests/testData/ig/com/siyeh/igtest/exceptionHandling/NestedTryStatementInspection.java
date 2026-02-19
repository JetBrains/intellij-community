package com.siyeh.igtest.exceptionHandling;

public class NestedTryStatementInspection
{
    public void foo()
    {
        try
        {
           try
           {
               System.out.println("NestedTryStatementInspection.foo");
           }
           finally
           {

           }
        }
        finally
        {

        }
    }
}
