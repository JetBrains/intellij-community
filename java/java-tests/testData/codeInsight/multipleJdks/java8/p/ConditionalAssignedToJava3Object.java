package p;
class B {
  void bar(A a) {
    a.foo(true ? "1" : 1);
  }
}