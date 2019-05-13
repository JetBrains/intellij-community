
interface Item<K, V>{}
interface Holder<A, B> {
  boolean apply(Item<? extends A, ? extends B> i);
}
class C {
  void f(Holder<?,?> h) {
    h.apply(create());
  }

  private <L, M> Item<L,M> create() {
    return null;
  }
}