// "Replace with 'removals.forEach(source::remove)'" "true-preview"

import java.util.*;

class Test {
  void foo(Set<Integer> source, List<Integer> removals) {
    removals.forEach(source::remove);
  }
}
