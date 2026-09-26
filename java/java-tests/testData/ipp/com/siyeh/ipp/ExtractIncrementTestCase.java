package com.siyeh.ipp;

public class ExtractIncrementTestCase {
    public void foo() {
        int i = 3;
        System.out.println(i++);
        System.out.println(++i);
        System.out.println(i--);
        System.out.println(--i);
    }
}
