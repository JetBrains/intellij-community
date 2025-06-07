interface A {
  default void m() { }
}

interface B {
  static void m() { }
}

interface C {
  void m();
}

<error descr="Class 'D' must either be declared abstract or implement abstract method 'm()' in 'C'">class D implements A, B, C</error> { }