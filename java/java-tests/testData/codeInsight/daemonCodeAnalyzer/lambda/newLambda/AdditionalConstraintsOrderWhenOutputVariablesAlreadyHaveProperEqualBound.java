import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

class Test {
  {
    final Map<Comparable, List<Collection<?>>> families = sortingMerge(<error descr="Incompatible equality constraint: Integer and Comparable">(s) -> 0</error>);
  }

  private <C extends Comparable<C>, T> Map<C, List<T>> sortingMerge(Function<T, C> keyFunction) {

    return new HashMap<C, List<T>>();
  }
}
