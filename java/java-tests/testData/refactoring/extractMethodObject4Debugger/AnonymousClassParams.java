interface I {
  void foo(int i) {}
}
class Sample {
  void foo() {
    System.out.println("hello <caret>world");
  }
}