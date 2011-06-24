class A{
  private void f(){}
}

class B extends A{
  void f(){}
}

class Test{
  static foo(){
    A a;
    ((B)a).f();
  }
}
