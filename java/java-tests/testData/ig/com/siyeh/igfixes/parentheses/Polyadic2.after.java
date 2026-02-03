package com.siyeh.ipp.parentheses;

class Polyadic {
    boolean foo(int a, int b, int c) {
        return a + b<caret> - c;
    }
}