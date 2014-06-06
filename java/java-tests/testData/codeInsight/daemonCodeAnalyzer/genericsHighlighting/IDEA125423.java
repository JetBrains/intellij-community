class FooBar<T> {
  void foo(final FooBar<?> fooBar){
    fooBar.supertype<error descr="'supertype(java.lang.Class<capture<?>>)' in 'FooBar' cannot be applied to '(java.lang.Class<java.lang.Iterable>)'">(Iterable.class)</error>;
  }

  void foo1(final FooBar<? super T> fooBar){
    fooBar.supertype<error descr="'supertype(java.lang.Class<capture<? super T>>)' in 'FooBar' cannot be applied to '(java.lang.Class<java.lang.Iterable>)'">(Iterable.class)</error>;
  }

  void foo2(final FooBar<? extends T> fooBar){
    fooBar.supertype<error descr="'supertype(java.lang.Class<? super capture<? extends T>>)' in 'FooBar' cannot be applied to '(java.lang.Class<java.lang.Iterable>)'">(Iterable.class)</error>;
  }

  void supertype(Class<? super T> superclass) {}
}
