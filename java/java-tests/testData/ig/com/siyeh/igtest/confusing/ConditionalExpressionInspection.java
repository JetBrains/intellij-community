package com.siyeh.igtest.confusing;

public class ConditionalExpressionInspection
{

    public static void main(String[] args)
    {
        final int foo = (3<5)?3:5;
        System.out.println("foo = " + foo);
    }
}
