package com.siyeh.igtest.internationalization;

public class StringLiteralInspection
{
    public StringLiteralInspection()
    {
    }

    public void foo()
    {
        final Exception string6 = new Exception("foo");
        final String string1 = "foo" + "bar";
        final String string2 = "foo";

        final String string3 = bar("foo");
        assert false: "false";
        final String string4 = "";
        final String string5 = " ";
    }

    private String bar(String s)
    {
        return s;
    }
}