package com.siyeh.igtest.controlflow.negated_if_else;

public class NegatedIfElse
{

    public static void main(String[] args)
    {
        final boolean foo = baz();
        final boolean bar = baz();
        final Object bazoom = new Object();
        <warning descr="'if' statement with negated condition">if</warning>(foo != bar)
        {
           System.out.println("");
        }
        else
        {
           System.out.println("");
        }
        if(bazoom != null)
        {
           System.out.println("");
        }
        else
        {
          System.out.println("");
        }
    }

    private static boolean baz()
    {
        return true;
    }

    void foo(int i) {
        if (i != 0) {
        }
    }
}
