class C {
  Object foo(boolean b) {
    if (b) {
      <selection>return A.getInstance();</selection>
    } else {
      return B.getInstance();
    }
  }
}
class A {
  static A getInstance() {
    return new A();
  }
}
class B extends A {
  static B getInstance() {
    return new B();
  }
}