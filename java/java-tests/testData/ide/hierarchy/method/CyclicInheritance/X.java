interface A extends C {
  void foo();
}
interface B extends A {
  void foo();
}
interface C extends B {
  void foo();
}
class D implements C {
  void foo();
}