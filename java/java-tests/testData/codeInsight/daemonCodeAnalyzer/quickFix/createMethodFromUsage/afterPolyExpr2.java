// "Create method 'getKey'" "true-preview"
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class X {
  void x() {
    Map<Double, List<String>> map = Stream.of("x").collect(Collectors.groupingBy(getKey()));
  }

    private Function<? super String, Double> getKey() {
        return null;
    }
}