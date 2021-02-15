interface Predicate<<warning descr="Type parameter 'T' is never used">T</warning>> {}
class Foo {
  static <C extends I> void process(Predicate<C> <warning descr="Parameter 'predicate' is never used">predicate</warning>, C <warning descr="Parameter 'context' is never used">context</warning>) {}
}
interface I {}
class Bar implements I {
  Predicate p;
  {
    <warning descr="Unchecked call to 'process(Predicate<C>, C)' as a member of raw type 'Foo'">Foo.process</warning>(p, new Bar());
  }
}
class Bar2 {
  void m(Provider provider) {
    <warning descr="Unchecked call to 'provide(Predicate<String>)' as a member of raw type 'Bar2.Provider'">provider.provide</warning>(p());
    provider.provide1(1);
  }

  static class Provider<<warning descr="Type parameter 'T' is never used">T</warning>> {
    void provide(Predicate<String> <warning descr="Parameter 'consumer' is never used">consumer</warning>){ }
    void provide1(Integer <warning descr="Parameter 'i' is never used">i</warning>) {}
  }

  <K> Predicate<K> p() {
    return null;
  }
}
