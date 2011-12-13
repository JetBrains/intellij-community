import java.io.IOException;

class InheritedDocInThrows1 extends A{
  /**
   * @throws java.io.IOException comment
   */
  void foo() throws IOException {
    super.foo();
  }
}

class A {
  /**
   * @throws java.io.IOException la-la-la
   */
  void foo() throws IOException {
  }
}
