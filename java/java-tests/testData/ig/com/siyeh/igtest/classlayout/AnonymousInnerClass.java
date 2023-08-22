package com.siyeh.igtest.classlayout;

public class AnonymousInnerClass {

    enum Operation {
        PLUS   { double eval(double x, double y) { return x + y; } },
        MINUS  { double eval(double x, double y) { return x - y; } },
        TIMES  { double eval(double x, double y) { return x * y; } },
        DIVIDE { double eval(double x, double y) { return x / y; } };

        // Do arithmetic op represented by this constant
        abstract double eval(double x, double y);
    }
}