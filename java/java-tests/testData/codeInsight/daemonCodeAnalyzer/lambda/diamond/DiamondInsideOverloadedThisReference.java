
class A<T> {}
class B<K> extends A<String> {}

class X<L> {
  public X(A<L> b1) {
    this(new B<>());
  }

  public X(B<L> b2) {}
}