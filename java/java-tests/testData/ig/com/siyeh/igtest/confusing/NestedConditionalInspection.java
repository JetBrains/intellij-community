package com.siyeh.igtest.confusing;

public class NestedConditionalInspection
{
    public NestedConditionalInspection()
    {
    }

    public void foo()
    {
       final int val = bar()?3:(bar()?4:5);
        System.out.println("val = " + val);
    }

    private boolean bar()
    {
       return true;
    }
}
