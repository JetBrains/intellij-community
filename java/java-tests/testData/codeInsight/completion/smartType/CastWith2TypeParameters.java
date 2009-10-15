interface Foo<T,V> {}

  class Bar<T,V> {
    Bar(Foo<T,V> foo) {}
  }

  class Main {
    Foo foo;
    Bar<String,String> bar = new Bar<String,String>((<caret>foo);
  }
