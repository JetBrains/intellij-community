// "Make 'I.foo' static" "true"
interface I {
  static void foo() {
    System.out.println();
  }
}

class B {
  {
    I.foo();
  }
}