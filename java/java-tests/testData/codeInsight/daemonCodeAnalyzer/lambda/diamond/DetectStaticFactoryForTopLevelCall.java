
import java.util.Optional;
import java.util.stream.Collector;
import java.util.stream.Collectors;

class Collectinator<T, A, R> {

  private Collectinator(Collector<T, A, R> collector) { }

  {

    Collectinator<Foo, ?, Optional<Foo>> lastDate =
      new Collectinator<>(Collectors.maxBy(Foo::compareTo));

  }

  static class Bar {}
  static class Foo extends Bar{
    public int compareTo(Bar other) {
      return -1;
    }
  }
}