class A{
  private void f(){}
}

class B extends A{
  void f(){}
}

class Test{
  static void foo(){
    A a = null;
    ((B)a).f();
  }
}
