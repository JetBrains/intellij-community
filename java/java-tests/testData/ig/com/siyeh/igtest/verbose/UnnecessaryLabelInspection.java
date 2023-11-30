package com.siyeh.igtest.verbose;

public class UnnecessaryLabelInspection
{

    public UnnecessaryLabelInspection()
    {

    }

    public void foo()
    {
        int x = 0;
        Label:
           foo();

    }
}