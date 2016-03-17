// "Replace with lambda" "true"

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

class Test {
  {
    Map<Comparable, List<Collection<?>>> families = sortingMerge(new Fun<caret>ction<Collection<?>, Comparable>() {
          @Override
          public Comparable<Integer> apply(Collection<?> family) {
            return new Integer(0);
          }
        });
  }

  <C extends Comparable<C>, T> Map<C, List<T>> sortingMerge(
    Function<T, C> keyFunction) {

    return new HashMap<C, List<T>>();
  }
}
