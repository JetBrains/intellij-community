package com;

class Test {
  static void f(String s, Object o){}

  void foo(){
    Object o = null;
    f((String)o, (<warning descr="Casting 'o' to 'String' is redundant">String</warning>)o);
  }
}
