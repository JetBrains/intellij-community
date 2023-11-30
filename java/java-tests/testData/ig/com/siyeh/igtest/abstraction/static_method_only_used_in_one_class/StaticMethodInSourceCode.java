package com.siyeh.igtest.abstraction;

public class StaticMethodInSourceCode {

  public static void <warning descr="Static method 'methodWithSomePrettyUniqueName()' is only used from class 'OneClass'">methodWithSomePrettyUniqueName</warning>() {

  }
  public void someMethod(Object o) {}

  public static String t = "1";
}

class OneClass {
  static {
    StaticMethodInSourceCode.methodWithSomePrettyUniqueName();
  }
  public void someMethod(Object o) {}
}