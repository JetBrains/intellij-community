class A {
  public final void foo() {}
}
class B extends A {}
class C {
  void f(B b) {
    <selection>b</selection>.foo();
  }
}