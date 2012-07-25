interface Action<A>{}
interface X { <T> T execute(Action<T> a); }
interface Y { <S> S execute(Action<S> a); }
interface Foo extends X, Y {}
  // Functional: signatures are "the same"