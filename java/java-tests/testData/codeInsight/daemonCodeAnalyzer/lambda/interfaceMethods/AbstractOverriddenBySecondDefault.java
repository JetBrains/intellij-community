
interface I {
  void foo();
}

interface I1 extends I {
  //void foo();
}

interface J {
  default void foo() {}
}

interface A extends J, I {
  @Override
  default void foo() {}
}

interface O extends J {
}

interface  R extends O, A, I1{}
