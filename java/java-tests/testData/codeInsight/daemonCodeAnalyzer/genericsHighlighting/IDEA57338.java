abstract class A {
  abstract <T> void foo();
}

abstract class B extends A {
  void foo()
  {
    this.<Integer>foo();
  }
}

abstract class C {
  void foo()
  {
    this.<error descr="Method 'foo()' does not have type parameters"><Integer></error>foo();
  }
}