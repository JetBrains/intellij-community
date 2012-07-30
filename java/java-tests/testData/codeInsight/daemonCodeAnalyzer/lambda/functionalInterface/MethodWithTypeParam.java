interface Foo { <T> T execute(Action<T> a); }
interface Action<A>{}
  // Functional