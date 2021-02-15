// "Replace with '(flag ? removals1 : removals2).forEach(source::remove)'" "true"

import java.util.*;

class Test {
  void foo(Set<Integer> source, List<Integer> removals1, List<Integer> removals2, boolean flag) {
    source.removeAll<caret>(flag ? removals1 : removals2);
  }
}
