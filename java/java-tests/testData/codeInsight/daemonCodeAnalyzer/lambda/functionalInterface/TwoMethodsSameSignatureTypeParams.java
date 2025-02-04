interface Action<A>{}
interface X { <T> T execute(Action<T> a); }
interface Y { <S> S execute(Action<S> a); }
@FunctionalInterface
interface Foo extends X, Y {}

class Use {
  // Functional: signatures are "the same"
  // but not lambda-compatible
  Foo foo = <error descr="Target method is generic">a -> null</error>;
}