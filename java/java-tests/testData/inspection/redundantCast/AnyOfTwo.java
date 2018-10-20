package com;

class Test {
  static void f(Object s, Object o){}
  static void f(String s, String o){}

  void foo(){
    Object o = null;
    f((String)o, (String)o);
  }
}
