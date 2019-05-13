import java.util.List;
import java.util.function.Function;

class A {

  void m(List<String> l) {
    Function<String, String> function = x -> x;

    boolean strings = l.stream().map(x -> x).findFirst().isPresent();
  }

}