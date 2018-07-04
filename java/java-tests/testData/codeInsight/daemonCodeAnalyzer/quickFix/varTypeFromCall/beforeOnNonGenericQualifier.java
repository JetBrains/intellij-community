// "Change field 'f' type to 'java.lang.String'" "true"
class Test {
  int f;
  void foo(A a)  {
    a.foo(<caret>f);
  }
}

class A {
  void foo(String s) {}
}