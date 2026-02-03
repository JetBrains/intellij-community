// "Swap 'filter()' and 'map()'" "true-preview"

import java.util.List;
import java.util.stream.Collectors;

class X {
  void test(List<List<String>> list) {
    list.stream().map(l -> l.toString()).filter(string -> string.isEmpty()).collect(Collectors.toList());
  }
}
