interface A {
  default void m() { }
}

interface B {
  static void m() { }
}

interface C {
  void m();
}

class <error descr="Class 'D' must either be declared abstract or implement abstract method 'm()' in 'C'">D</error> implements A, B, C { }