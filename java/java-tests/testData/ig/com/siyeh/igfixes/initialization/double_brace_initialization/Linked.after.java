class A {
  final C C = new C();

    {
        C.m();
        C.m();
    }
}
class B {
  void m() {}
}
class C extends B {}