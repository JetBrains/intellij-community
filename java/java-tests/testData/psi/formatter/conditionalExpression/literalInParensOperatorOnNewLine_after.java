package org.example;

public class A {
    void f() {
        String x = true
                ? ("hello"
                   + "world")
                : ("goodbye"
                   + "world");
    }
}