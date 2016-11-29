
import java.util.Collection;

class Test {

  void m(StringBuilder builder){
    builder.append(new Foo<>((Collection)null));
  }

  static class Foo<T> {
    public Foo(Collection<? extends T> c) {}
    public Foo(Foo<T> t) {}
  }
}