class Foo<Z> {
  void foo(final Bar baz) {
    Z z =  z(new Bar<String>(baz));
    <error descr="Incompatible types. Found: 'java.lang.Object', required: 'Z'">Z z1 = z(new Bar<>(baz));</error>
    <error descr="Incompatible types. Found: 'java.lang.Object', required: 'Z'">Z z2 = z(c(baz));</error>
    <error descr="Incompatible types. Found: 'java.lang.Object', required: 'Z'">Z z3 = z(this.<String>c(baz));</error>
  }

  <P> Bar<P> c(Bar<P> b) {
    return b;
  }

  private <X> Z z(Bar<X> b) {
    return null;
  }
}

class Bar<T> {
  public Bar(Bar<T> v) {
  }
}