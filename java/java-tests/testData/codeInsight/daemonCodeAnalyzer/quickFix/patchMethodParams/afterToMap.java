// "Cast lambda return to 'long'" "true-preview"
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class StreamFilter {
  void test() {
    Map<String, Long> map = Stream.of("a", "b", "c").collect(Collectors.toMap(s -> s, s -> (long) s.length()));
  }
}