public class Test {
  void foo() throws java.lang.Exception {
    throw new java.io.IOException();
  }

  void bar() throws java.io.IOException {
    throw new java.lang.Exception();
  }
}