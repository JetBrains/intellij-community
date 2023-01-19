// "Make 'I.foo()' static" "true-preview"
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