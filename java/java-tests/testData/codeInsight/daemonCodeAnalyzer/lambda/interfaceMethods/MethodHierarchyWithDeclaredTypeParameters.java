interface C<E> {}

interface L<E> extends C<E> {
  <T> L<T> foo();
}

interface L1<E> extends L<E>, C1<E> {
  @Override
  default <K> L<K> foo() {
    return null;
  }
}

interface C1<E> extends C<E> {
  default <M> C<M> foo() {
    return null;
  }
}

interface C2<E> extends C1<E> {}

interface L2<E> extends C2<E>, L1<E>, L<E> {}