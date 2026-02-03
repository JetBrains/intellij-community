package com.siyeh.ipp;

import junit.framework.TestCase;

public class JUnitTest extends TestCase {
    Object x;
    Object y;
    int z;
    
    public void test()
    {
          true;

        x == null;
        x == y;
        x.equals(y);
        x.equals("y");
        3 == z;
    }

    public void test2() {
        assertTrue(this != this);
    }
}
