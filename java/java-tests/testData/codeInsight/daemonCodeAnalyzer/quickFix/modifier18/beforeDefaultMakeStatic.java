// "Make 'I.foo' static" "true"
interface I {
  default void foo() {
    System.out.println();
  }
}

class B {
  {
    I.f<caret>oo();
  }
}