package com.siyeh.igfixes.classlayout.class_may_be_interface;

class X {
  void test() {
    abstract class <caret>Foo {}
    
    class Bar extends Foo {}
  }
}