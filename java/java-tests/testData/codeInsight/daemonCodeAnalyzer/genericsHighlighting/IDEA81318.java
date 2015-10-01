
import java.util.List;
import java.util.Map;

class Test {
  public static<F, S> void xyz() {
    class Pair<X, Y> {}
    class Role<F> {}

    Map<?, List<Pair<?, ?>>> map = null;
    Role<F> role = null;

    List<Pair<F, S>> result = <error descr="Inconvertible types; cannot cast 'java.util.List<Pair<?,?>>' to 'java.util.List<Pair<F,S>>'">(List<Pair<F, S>>) map.get(role)</error>;
  }
}