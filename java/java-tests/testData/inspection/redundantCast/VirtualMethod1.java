class A{
  void f(){}
}

class B extends A{
  void f(){}
}

class Test{
  static void foo(){
    A a = null;
    ((<warning descr="Casting 'a' to 'B' is redundant">B</warning>)a).f();
  }
}
