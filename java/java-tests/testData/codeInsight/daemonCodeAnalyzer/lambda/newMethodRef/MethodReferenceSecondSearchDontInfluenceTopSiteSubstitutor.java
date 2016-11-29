package pi;

import java.util.stream.Stream;

abstract class Foo<T> {
  public abstract <X> Foo<X> getAssociatedFoo();

  public abstract static class Bar<Z> {
    public Stream<Foo<Object>> getAssociatedFoos(Stream<Foo<Z>> stream) {
      return stream.map(Foo::getAssociatedFoo);
    }
  }
}