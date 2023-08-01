package com.siyeh.ipp.forloop.indexed;

class NewArray {
    void foo() {
        int[] ints = new int[3];
        for (int i = 0, intsLength = ints.length; i < intsLength; i++) {
            int k = ints[i];
        }
    }
}