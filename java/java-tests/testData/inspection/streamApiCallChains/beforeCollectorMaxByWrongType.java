// "Replace 'collect(maxBy())' with 'max()' (may change semantics when result is null)" "false"

import java.util.*;
import static java.util.stream.Collectors.maxBy;

public class Test {
  private static <T> Optional<T> maxOf(List<? extends T> list, Comparator<? super T> comparator) {
    return list.stream().col<caret>lect(maxBy(comparator));
  }
}