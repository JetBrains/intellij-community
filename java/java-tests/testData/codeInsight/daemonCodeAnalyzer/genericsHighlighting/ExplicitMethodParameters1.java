import java.util.*;

class Foo {
  interface Comparable<T> { }
  static <T extends Comparable<T>> void sort(T t) {}

  class C implements Comparable<C> {}
  class D implements Comparable<String> {}

  {
    Foo.<C>sort(new C());
    Foo.<<error descr="Type parameter 'Foo.D' is not within its bound; should implement 'Foo.Comparable<Foo.D>'">D</error>>sort(new D());
  }
}

