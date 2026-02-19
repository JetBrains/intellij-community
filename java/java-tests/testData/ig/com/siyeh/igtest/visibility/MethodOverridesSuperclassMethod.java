package com.siyeh.igtest.visibility;

public class MethodOverridesSuperclassMethod{
}

class Parent{
    void test(String s){
    }
}

class Child extends Parent{
    void test(String s){
    }

    void test(int x){  
    }
}