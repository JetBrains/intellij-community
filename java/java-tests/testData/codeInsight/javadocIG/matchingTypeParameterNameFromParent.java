import java.lang.Override;

class Foo {
  /**
   * @param <T> description
   */
  <T> void m(T t) {}
}

class Bar extends Foo {
  @Override
  <U> void m(U u) {}
}