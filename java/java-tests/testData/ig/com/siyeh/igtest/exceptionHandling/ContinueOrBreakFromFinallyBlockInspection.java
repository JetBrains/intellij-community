package com.siyeh.igtest.exceptionHandling;

public class ContinueOrBreakFromFinallyBlockInspection {
    public void foo() {
        while (true) {
            try {

            } finally {
                break;
            }
        }
    }

    public int bar() {
         while (true) {
            try {

            } finally {
                continue;
            }
        }
    }
}
