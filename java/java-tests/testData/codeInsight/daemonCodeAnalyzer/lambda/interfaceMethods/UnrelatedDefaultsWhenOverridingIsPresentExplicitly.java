interface A<E> {
  void foo(E item);
}

interface B1<E> extends A<E> {
  @Override
  default void foo(E item) {}
}

interface B2<E> extends A<E> {
  @Override
  default void foo(E item) {}
}

interface C1<E> extends B1<E> {}

interface C2<E> extends B2<E> {}

interface Bottom1<E> extends C2<E>, C1<E> {
  @Override
  default void foo(E item) {}
}
interface Bottom2<E> extends C2<E>, C1<E> {
  @Override
  void foo(E item);
}
interface <error descr="Bottom3 inherits unrelated defaults for foo(E) from types B2 and B1">Bottom3</error><E> extends C2<E>, C1<E> {}