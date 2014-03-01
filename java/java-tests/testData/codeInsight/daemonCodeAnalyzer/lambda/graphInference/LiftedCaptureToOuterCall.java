class Test {

  class Foo<K> {}

  void test(Foo<? extends String> p) {
    foo(bar(p)) ;
  }

  <T> T bar(Foo<T> p) {
    return null;
  }

  <K> K foo(K p) {
    return null;
  }
}
