package com.siyeh.igtest.confusing;

public class NegatedConditionalInspection
{

    public static void main(String[] args)
    {
        final boolean foo = baz();
        final boolean bar = baz();
        final Object bazoom = new Object();
        final boolean out = (foo != bar)?bar:foo;

    }

    private static boolean baz()
    {
        return true;
    }
}
