// "Change field 'f' type to 'String'" "true"
class Test {
  String f;
  void foo(A a)  {
    a.foo(f);
  }
}

class A {
  void foo(String s) {}
}