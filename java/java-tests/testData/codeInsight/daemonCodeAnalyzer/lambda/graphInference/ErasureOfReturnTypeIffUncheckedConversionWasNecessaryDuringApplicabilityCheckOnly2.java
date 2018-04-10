import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

class MyTest {

  public static <T, V> void map(Collection<? extends T> iterable,
                                Function<T, V> mapping) {}

  interface StubElement<P extends String> {
    P getPsi();
  }

  void foo(List<StubElement> stubs) {
    map(stubs, StubElement::getPsi);
  }

  void bar(Stream<StubElement> stream) {
    stream.<String>map(StubElement::getPsi).map(s -> s.substring(0));
  }
}