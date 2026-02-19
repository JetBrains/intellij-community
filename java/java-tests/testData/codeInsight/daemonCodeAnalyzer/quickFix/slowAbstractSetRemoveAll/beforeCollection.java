// "Replace with 'getRemovals().forEach(source::remove)'" "true-preview"

import java.util.*;

class Test {
  native List<String> getRemovals();

  void foo(Set<Integer> source) {
    source.removeAll<caret>(getRemovals());
  }
}
