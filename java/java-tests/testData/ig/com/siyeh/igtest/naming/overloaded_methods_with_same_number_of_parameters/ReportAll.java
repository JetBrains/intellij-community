package com.siyeh.igtest.naming.overloaded_methods_with_same_number_of_parameters;

class OverloadedMethodsWithSameNumberOfParameters {

    public void <warning descr="Multiple methods named 'foo' with the same number of parameters">foo</warning>(int i) {}
    public void <warning descr="Multiple methods named 'foo' with the same number of parameters">foo</warning>(String s) {}

    public void equals(String s) {}
}