// "Fix all 'Stream API call chain can be replaced with loop' problems in file" "true"

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

abstract class MyIterable implements Iterable<String> {
  Map<String, Double> calc(Iterable<String> iterable) {
      Map<String, Double> map = new HashMap<>();
      for (String s : iterable) {
          if (map.put(s, Double.valueOf(s + "0")) != null) {
              throw new IllegalStateException("Duplicate key");
          }
      }
      return map;
  }

  void foo() {
      long count = 0L;
      for (String s : this) {
          count++;
      }
  }

}
