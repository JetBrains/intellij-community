class A {
  public A(Object o) {
  }
}

class B {}

class C extends B {
  static {
    A a = new A(<error descr="'C.this' cannot be referenced from a static context">this</error>) {};
    A a1 = new A(<error descr="'C.super' cannot be referenced from a static context">super</error>.clone()) {};
  }

  {
    A a = new A(this);
  }
}