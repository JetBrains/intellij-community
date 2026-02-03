class A {
  void foo(String bar, int a) {}
  String zoo(int b) {}

  {
    foo(zoo(), <caret>)
  }
}