import java.util.List;
import java.util.function.Function;

class E<In> {
  E(List<In> l, Function<In, Integer> f, List<In> ff) {}

  void m(List<String> l, List ff){

    E<String> e = new E<>(l, o -> o.length(), ff);
  }
}
