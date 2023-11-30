class FooBar<T> {
  FooBar<List<T>> foo() {
    return null;
  }

  FooBar<T> bar() {return this;}

  void f(FooBar<String> fb) {
    foo().bar().toSt<caret>ring();
  }

}
