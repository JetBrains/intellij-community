// "Swap 'filter()' and 'map()'" "true"

import java.util.List;
import java.util.stream.Stream;

class X {
  void test(List<Integer> list) {
    Stream<Integer> stream = list.stream().map(i -> i.intValue()).filter(i -> i > 0);
  }
}
