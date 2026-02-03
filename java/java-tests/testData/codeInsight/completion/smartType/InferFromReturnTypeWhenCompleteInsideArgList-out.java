
class D<T> {
  D(T t) {}
  D<Integer> reference = new D<>(new Integer(hashCode()));
}