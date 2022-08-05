// "Cast lambda return to 'long'" "true-preview"
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class StreamFilter {
  void test() {
    Map<Long, List<String>> collect = Stream.of("xyz", "asfdasdfdasf", "dasfafasdfdf")
      .collect(Collectors.groupingBy(s -> (long) s.length()));
  }
}