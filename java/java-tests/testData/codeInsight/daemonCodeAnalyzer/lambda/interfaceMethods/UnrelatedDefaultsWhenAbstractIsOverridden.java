
interface I {
  void foo();
}

interface I1 extends I {
}

interface J {
  default void foo() {}
}

interface A extends J, I {
  @Override
  default void foo() {}
}

interface O extends J {
  default void foo() {}
}

interface  <error descr="R inherits unrelated defaults for foo() from types O and A">R</error> extends O, A, I1{}
