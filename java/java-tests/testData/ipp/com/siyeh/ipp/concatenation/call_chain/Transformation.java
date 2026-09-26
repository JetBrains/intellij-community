class FooBar<T> {
  <K> FooBar<K> foo(K k) {
    return null;
  }

  FooBar<T> bar() {return this;}

  void f(FooBar<String> fb) {
    fb.foo(1).bar().toSt<caret>ring();
  }

}
