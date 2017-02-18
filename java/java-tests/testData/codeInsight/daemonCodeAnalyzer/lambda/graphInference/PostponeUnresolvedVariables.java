
import java.util.Comparator;
import java.util.function.Supplier;

class Test {


  void f(Supplier<Comparable> m) {
    g(m, Comparator.naturalOrder());
  }
  <K> void g(Supplier<K> f, Comparator<K> c) {}

}
