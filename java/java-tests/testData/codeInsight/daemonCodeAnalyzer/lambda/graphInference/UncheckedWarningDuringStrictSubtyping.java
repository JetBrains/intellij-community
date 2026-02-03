import java.util.List;

interface FacetId<T extends List> {}

abstract class One {
  public abstract <F extends List<C>, C extends One> void findFacetType(FacetId<F> typeId);
  private void addSubFacet(FacetId<?> underlyingType) {
    findFacetType(underlyingType);
  }
}

class Bar<X extends Foo> {}
class Foo<C> {}
class Main {
  void foo(Bar<?> a) {
    m(a);
  }

  <X extends Foo<C>, C> void m(Bar<X> a) {
  }
}