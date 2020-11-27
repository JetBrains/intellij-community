// "Replace with 'removals.forEach(source::remove)'" "true"

import java.util.*;

class Test {
  void foo(Set<Integer> source, List<Integer> removals) {
    source.removeAll<caret>(removals);
  }
}
