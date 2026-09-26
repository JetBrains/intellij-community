// "Make 'I.foo()' public" "true-preview"
interface I {
  default void foo() { }
}

class A implements I {
  {
    this.foo();
  }
}