// "Swap 'filter()' and 'map()'" "true-preview"

import java.util.List;
import java.util.stream.Collectors;

class X {
  void test(List<List<String>> list) {
    list.stream().filter((List<String> l) -> l.toString().isEmpty()).map<caret>(l -> l.toString()).collect(Collectors.toList());
  }
}
