class Test<X extends Getter<?, ?> & Runnable> {}

interface Supplier<K> {
  K get();
}

interface Getter<C, S extends C> extends Supplier<C> {
  public S get();
}
