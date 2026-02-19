package com.siyeh.igtest.internationalization;

public class NumericToString
{
    public NumericToString()
    {
    }

    public void foo()
    {
        final Integer j = new Integer(0);
        j.<warning descr="'Number.toString()' called in an internationalized context">toString</warning>();
        final Short k = new Short((short) 0);
        k.<warning descr="'Number.toString()' called in an internationalized context">toString</warning>();
        final Long m = new Long(0);
        m.<warning descr="'Number.toString()' called in an internationalized context">toString</warning>();
        final Double d = new Double(0);
        d.<warning descr="'Number.toString()' called in an internationalized context">toString</warning>();
        final Float f = new Float(0);
        f.<warning descr="'Number.toString()' called in an internationalized context">toString</warning>();
        final Object g = new Float(0);
        g.toString();
        final Boolean b = new Boolean(true);
        b.toString();
    }
}