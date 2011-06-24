package com;

public class Test {
  static void f(String s, Object o){}

  void foo(){
    Object o;
    f((String)o, (String)o);
  }
}
