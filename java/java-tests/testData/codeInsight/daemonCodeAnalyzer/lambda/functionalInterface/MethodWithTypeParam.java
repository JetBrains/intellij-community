@FunctionalInterface
interface Foo { <T> T execute(Action<T> a); }
interface Action<A>{}

class Use {
  // Functional but not lambda-compatible
  Foo foo = <error descr="Target method is generic">a -> null</error>;
}