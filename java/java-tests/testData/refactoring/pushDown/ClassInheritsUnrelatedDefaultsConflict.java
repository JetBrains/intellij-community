
interface A {
  default void foo() {}
}

interface I {
  default void foo() { }
}

class B implements I, A {
  @Override
  public void fo<caret>o() { }
}

class C extends B {
}