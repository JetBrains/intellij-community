// "Fix all 'Stream API call chain can be replaced with loop' problems in file" "true"

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

abstract class MyIterable implements Iterable<String> {
  Map<String, Double> calc(Iterable<String> iterable) {
    Map<String, Double> map = StreamSupport.stream(iterable.spliterator(), false)
      .<caret>collect(Collectors.toMap(
        s -> s,
        s -> Double.valueOf(s + "0")
      ));
    return map;
  }

  void foo() {
    long count = StreamSupport.stream(spliterator(), false).count();
  }

}
