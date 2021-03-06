// "Replace with 'removals.forEach(source::remove)'" "true"

import java.util.*;

class Test {
  void foo(Set<Integer> source, List<Integer> removals) {
    int setSize = source.size();
    int listSize = removals.size();
    if ((setSize == 100000 || setSize == 41) && listSize == 42) {
      removals.forEach(source::remove);
    }
  }
}
