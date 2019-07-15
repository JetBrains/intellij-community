interface Predicate<<warning descr="Type parameter 'T' is never used">T</warning>> {}
class Foo {
  static <C extends I> void process(Predicate<C> <warning descr="Parameter 'predicate' is never used">predicate</warning>, C <warning descr="Parameter 'context' is never used">context</warning>) {}
}
interface I {}
class Bar implements I {
  Predicate p;
  {
    Foo.process(p, new Bar());
  }
}
