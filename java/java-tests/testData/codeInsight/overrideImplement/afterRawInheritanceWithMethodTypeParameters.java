class Foo<T> {
  <S> S foo(T foo) {
    return null;
  }
}

class Bar<S> extends Foo {
    @Override
    Object foo(Object foo) {
        <selection>return super.foo(foo);</selection>
    }
}