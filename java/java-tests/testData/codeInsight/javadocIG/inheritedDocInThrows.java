import java.io.IOException;

class InheritedDocInThrows extends A{
  /**
   * @throws IOException {@inheritDoc}
   */
  void foo() throws IOException {
    super.foo();
  }
}

class A {
  /**
   * @throws IOException la-la-la
   */
  void foo() throws IOException {
  }
}
