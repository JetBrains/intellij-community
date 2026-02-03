package com.siyeh.igtest.verbose;

public class ConstantIfStatementInspection {
    public void foo()
    {
        if(false)
        {
            System.out.println("1");
        }
        else
            System.out.println("2");
    }
}
