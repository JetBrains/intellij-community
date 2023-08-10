// "Convert record to class" "true-preview"
package com.example;

interface Runnable {}

final class X implements Runnable {
    X() {
    }

    @Override
    public boolean equals(Object obj) {
        return obj == this || obj != null && obj.getClass() == this.getClass();
    }

    @Override
    public int hashCode() {
        return 1;
    }

    @Override
    public String toString() {
        return "X[]";
    }
}