// "Create method 'getKey'" "true-preview"
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class X {
  void x() {
    Map<Double, List<String>> map = Stream.of("x").collect(Collectors.groupingBy(k -> getKey(k)));
  }

    private Double getKey(String k) {
        return null;
    }
}