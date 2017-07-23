// "Make 'I.foo' public" "true"
interface I {
  default void foo() { }
}

class A implements I {
  {
    this.foo();
  }
}