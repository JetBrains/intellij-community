package com.red;

public class C extends B {
    protected void add(B nodeGrid) {
        nodeGrid.getHeader().foo(); // no error here since we are checking getHeader() method accessibility rather than Header class
    }
}
