// "Swap 'filter()' and 'map()'" "true"

import java.util.List;
import java.util.stream.Stream;

class X {
  void test(List<Integer> list) {
    Stream<Integer> stream = list.stream().filter(i -> i.intValue() > 0).map<caret>(i -> i.intValue());
  }
}
