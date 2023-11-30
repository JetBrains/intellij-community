package com.siyeh.igtest.confusing;

import java.util.NoSuchElementException;

public class IncrementDecrementInspection
{
    public IncrementDecrementInspection()
    {
    }

    public void foo()
    {
        final int[] baz = new int[3];
        int i = 0;
        i++;
        final int val = baz[i++];
        final int val2 = baz[i--];
        final int val3 = baz[++i];
        final int val4 = baz[--i];
        System.out.println("i = " + i++);
        System.out.println("val = " + val);
    }

    public void bar()
    {
        for(int i = 0, j = 0; i < 10; i++, j += 2)
        {
            System.out.println(i++);
        }
    }

    private int index;

    public Object next(Object[] data) {
        if (index < data.length) {
            return data[index++]; // Value of post-increment is used, will be replaced with "return data[index];"
        } else {
            throw new NoSuchElementException();
        }
    }
}