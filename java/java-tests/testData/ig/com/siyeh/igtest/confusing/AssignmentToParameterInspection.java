package com.siyeh.igtest.confusing;

public class AssignmentToParameterInspection
{
    public AssignmentToParameterInspection()
    {
    }

    public void fooBar(int bar, int baz)
    {
        try {
            bar = 0;
            System.out.println("bar = " + bar);
            baz = 0;
            System.out.println("baz = " + baz);
            baz++;
            --baz;
        } catch (Exception e) {
             e.printStackTrace();
        }

    }
}
