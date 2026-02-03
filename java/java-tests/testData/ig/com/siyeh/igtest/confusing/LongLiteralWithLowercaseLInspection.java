package com.siyeh.igtest.confusing;

public class LongLiteralWithLowercaseLInspection
{
    public static final long s_foo = 3l;
    public static final long s_bar = 3L;

    public LongLiteralWithLowercaseLInspection()
    {
    }

    public void foo()
    {
        System.out.println("s_bar = " + s_bar);
        System.out.println("s_foo = " + s_foo);

        final long foo = 3l;
        final long bar = 3L;
        System.out.println("bar = " + bar);
        System.out.println("foo = " + foo);
    }
}
