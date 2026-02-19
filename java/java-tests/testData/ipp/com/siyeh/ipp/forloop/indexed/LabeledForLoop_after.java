package com.siyeh.ipp.forloop.indexed;

class LabeledForLoop {

    int[] getArr() {
        int[] arr = getArr();
        Label:
        for (int i = 0, arrLength = arr.length; i < arrLength; i++) {
            int x = arr[i];
        }
        return new int[]{1};
    }
}
