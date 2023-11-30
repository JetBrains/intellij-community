package com.siyeh.igtest.bugs;

public class StringEqualsInspection
{
    public StringEqualsInspection()
    {
    }

    public void foo()
    {
        if ("foo" == "bar")
        {

        }

        String foo = "foo";
        String bar = bar("bar");
        if (foo == bar)
        {

        }
        if (bar == null)
        {

        }
        if (null == bar)
        {

        }
    }

    private String bar(String s)
    {
        return s;
    }
}
