class Foo<Z> {
  void foo(final Bar baz) {
    Z z =  z(new Bar<String>(baz));
    Z z1 = <error descr="Incompatible types. Found: 'java.lang.Object', required: 'Z'">z</error>(new Bar<>(baz));
    Z z2 = <error descr="Incompatible types. Found: 'java.lang.Object', required: 'Z'">z</error>(c(baz));
    Z z3 = <error descr="Incompatible types. Found: 'java.lang.Object', required: 'Z'">z</error>(this.<String>c(baz));
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