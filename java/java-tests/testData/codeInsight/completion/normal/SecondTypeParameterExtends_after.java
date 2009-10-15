interface Foo<T,V> {}

  class Bar<T,V> {
    Bar(Foo<? extends T, ? extends <caret>> foo) {}
  }

