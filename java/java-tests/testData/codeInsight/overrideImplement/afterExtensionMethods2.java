interface A {
  default void foo() {}
}

interface B1 extends A {}

class B implements A {}

class C extends B implements B1 {
    @Override
    public void foo() {
        <selection><caret>super.foo();</selection>
    }
}
