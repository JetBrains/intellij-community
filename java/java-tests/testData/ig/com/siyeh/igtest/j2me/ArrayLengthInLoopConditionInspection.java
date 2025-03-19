package com.siyeh.igtest.j2me;

public class ArrayLengthInLoopConditionInspection {
    public void foo() {
        int[] bar = new int[6];
        for (int i = 0; i < bar.length; i++) {

        }
        while (bar.length != 4) {
            foo();
        }

        do {
            foo();
        }
        while (bar.length != 4);

    }

}
