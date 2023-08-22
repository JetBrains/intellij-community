// "Swap 'filter()' and 'map()'" "false"

import java.util.List;
import java.util.stream.Stream;

class X {
  void test(List<Integer> list) {
    Stream<Number> stream = list.stream().filter(x -> x.intValue() > 0).map<caret>(x -> x.intValue());
  }
}
