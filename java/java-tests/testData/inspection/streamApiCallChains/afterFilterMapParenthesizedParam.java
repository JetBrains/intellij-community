// "Swap 'filter()' and 'map()'" "true"

import java.util.List;
import java.util.stream.Collectors;

class X {
  void test(List<List<String>> list) {
    list.stream().map(l -> l.toString()).filter(s -> s.isEmpty()).collect(Collectors.toList());
  }
}
