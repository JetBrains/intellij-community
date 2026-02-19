// "Fix all 'Call to 'set.removeAll(list)' may work slowly' problems in file" "false"

import java.util.*;

class Test {
  void foo(Set<Integer> source, List<Integer> removals) {
    if (removals.isEmpty()) {
      source.removeAll<caret>(removals);
    }
  }
}
