package com;

public class Test {
  static void f(Object s, Object o){}
  static void f(String s, String o){}

  void foo(){
    Object o;
    f((String)o, (String)o);
  }
}
