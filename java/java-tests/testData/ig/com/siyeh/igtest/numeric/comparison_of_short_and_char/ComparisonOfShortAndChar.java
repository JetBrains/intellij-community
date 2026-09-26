package com.siyeh.igtest.numeric.comparison_of_short_and_char;

public class ComparisonOfShortAndChar
{

    public int foo()
    {
        char bar = 'c';
        short baz = (short) bar2();
        final boolean isEqual = <warning descr="Equality comparison 'bar == baz' of short and char values">bar == baz</warning>;
        System.out.println("isEqual = " + isEqual);
        final boolean isNotEqual = <warning descr="Equality comparison 'bar != baz' of short and char values">bar != baz</warning>;
        System.out.println("isEqual = " + isEqual);
        return 3;
    }

    private char bar2()
    {
        return (char) 3;
    }
}
