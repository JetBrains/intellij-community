package com.siyeh.igtest.confusing;

public class AssignmentToCatchParameterInspection
{
    public AssignmentToCatchParameterInspection()
    {
    }

    public void fooBar(int bar, int baz)
    {
        try {
        } catch (Exception e) {
            e = new Exception();
            e.printStackTrace();
        }

    }
}
