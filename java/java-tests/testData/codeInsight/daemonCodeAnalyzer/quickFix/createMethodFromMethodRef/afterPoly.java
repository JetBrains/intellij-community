// "Create method 'getKey'" "true"

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class X {
  void x() {
    Map<Double, List<String>> map = Stream.of("x").collect(Collectors.groupingBy(this::getKey));
  }

    private Double getKey(String s) {
        return null;
    }
}
