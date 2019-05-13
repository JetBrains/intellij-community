class ImmutableSet<E> {
  void foo(final ImmutableSet<Class<String>> of) {
    Object types = <error descr="Inconvertible types; cannot cast 'ImmutableSet<java.lang.Class<java.lang.String>>' to 'ImmutableSet<java.lang.Class<?>>'">(ImmutableSet<Class<?>>) of</error>;
  }
}