interface Base<E> {}
interface Base2<E> {}

class A<E extends Cloneable> {
  <P, K extends Base<E>&Base2<P>> void m(K k, P p) {
    E e = null;
    m(k, e);
  }

  void m(Base<E> k, E e) {}
}
