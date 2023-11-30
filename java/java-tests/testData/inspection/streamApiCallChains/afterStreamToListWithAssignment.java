// "Replace 'collect(toList())' with 'toList()'" "true-preview"

import java.util.ArrayList;
import java.util.List;
import java.util.stream.*;

class Test {
  void foo(List<String> o) {
    List<Integer> collect;
    if (o.size() == 1) {
      collect = o.stream().map(t -> t.length()).toList();
    } else {
      collect = new ArrayList<>();
    }
  }

}