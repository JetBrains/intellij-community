import java.lang.Override;

class Foo {
  /**
   * @param foo description
   */
  void m(int foo) {}
}

class Bar extends Foo {
  @Override
  void m(int bar) {}
}