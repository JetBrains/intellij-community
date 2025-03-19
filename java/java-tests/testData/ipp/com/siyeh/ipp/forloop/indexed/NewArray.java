package com.siyeh.ipp.forloop.indexed;

class NewArray {
    void foo() {
        <caret>for (int k : new int[3]) {
        }
    }
}