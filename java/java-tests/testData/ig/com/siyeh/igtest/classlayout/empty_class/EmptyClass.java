package com.siyeh.igtest.classlayout.emptyclass;

import java.io.Serializable;

public class EmptyClass {
    {
      final java.util.ArrayList<String> stringList = new java.util.ArrayList<String>() {};
      System.out.println("");
    }
}
class MyList extends java.util.ArrayList<String> {}
class MyException extends java.lang.Exception {}
abstract class <warning descr="Class 'ReportMe' is empty">ReportMe</warning> implements java.util.List {}
abstract class <warning descr="Class 'Empty1' is empty">Empty1</warning> extends <error descr="No interface expected here">Serializable</error> {}

interface Interface {
  default void method() {
    new Interface() { };
  }
}
class EmptyClassTest implements Interface {
}
enum <warning descr="Enum 'EmptyEnum' is empty">EmptyEnum</warning> {}