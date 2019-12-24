class A {
  static class B extends C {
  }

  static void bar() {}
}

class C {

    void foo() {
      A.bar();
    }
}
