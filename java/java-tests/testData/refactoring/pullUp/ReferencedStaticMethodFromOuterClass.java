class A {
  static class B extends C {
    void <caret>foo() {
      bar();
    }
  }

  static void bar() {}
}

class C {

}
