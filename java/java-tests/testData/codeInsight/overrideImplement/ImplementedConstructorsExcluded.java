class A {
  A() { }
  A(String s) { }
}

class B extends A {
  B() { super(); }
  <caret>
}
