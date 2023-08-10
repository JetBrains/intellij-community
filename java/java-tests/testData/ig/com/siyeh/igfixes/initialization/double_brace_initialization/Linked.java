class A {
  final C C = new <caret>C() {{
    m();
    m();
  }};
}
class B {
  void m() {}
}
class C extends B {}