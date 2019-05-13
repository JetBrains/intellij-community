import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Collector;
import java.util.stream.Collectors;

class Collectinator<T, A, R> {
  Collectinator(Collector<T, A, R> collector) {
  }

  static <K, L, M> Collectinator<K, L, M> create(Collector<K, L, M> c) {
    return new Collectinator<K, L, M>(c);
  }

  public static void foo(Comparator<Foo> compareTo) {
    Collectinator<Foo, ?, Optional<Foo>> foo  = new Collectinator< >(Collectors.maxBy(compareTo));
    Collectinator<Foo, ?, Optional<Foo>> foo1 = Collectinator.create(Collectors.maxBy(compareTo));
  }
}

class Foo {}