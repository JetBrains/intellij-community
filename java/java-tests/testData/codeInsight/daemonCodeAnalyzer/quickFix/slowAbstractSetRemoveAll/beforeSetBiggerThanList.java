// "Replace with 'removals.forEach(source::remove)'" "false"

import java.util.*;

class Test {
  void foo(Set<Integer> source, List<Integer> removals) {
    if (source.size() > removals.size()) {
      source.removeAll<caret>(removals);
    }
  }
}
