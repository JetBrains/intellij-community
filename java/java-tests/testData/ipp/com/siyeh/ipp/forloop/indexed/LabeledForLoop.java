package com.siyeh.ipp.forloop.indexed;

class LabeledForLoop {

    int[] getArr() {
        Label:
        <caret>for (int x: getArr()) {}
        return new int[]{1};
    }
}
