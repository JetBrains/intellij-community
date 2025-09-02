// "Convert to record class" "true-preview"
package com.example;

record X(boolean field) {

    public static boolean isField() {
        return true;
    }
}
