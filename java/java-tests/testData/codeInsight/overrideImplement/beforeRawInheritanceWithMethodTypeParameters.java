class Foo<T> {
  <S> S foo(T foo) {
    return null;
  }
}

class Bar<S> extends Foo {
  <caret>
}