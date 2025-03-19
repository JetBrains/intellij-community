package p;

import static p.A.foo;

class StaticImport {
  void example() {
    A.foo().toString();
  }
}

class A {
  static Object foo() {return null;}
}