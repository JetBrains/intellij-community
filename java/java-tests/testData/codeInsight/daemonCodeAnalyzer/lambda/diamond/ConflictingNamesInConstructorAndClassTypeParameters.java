
interface E {}
interface A<T> {}
class N<P extends E> implements A<P> {
  <P extends E> N(P p) {}
}

class K {
  <J extends E> A<J> f(J p) {
    return new N<>(p);
  }
}