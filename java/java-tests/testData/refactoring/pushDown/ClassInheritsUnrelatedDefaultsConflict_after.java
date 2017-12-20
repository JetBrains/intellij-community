
interface A {
  default void foo() {}
}

interface I {
  default void foo() { }
}

class B implements I, A {
}

class C extends B {
    @Override
    public void foo() { }
}