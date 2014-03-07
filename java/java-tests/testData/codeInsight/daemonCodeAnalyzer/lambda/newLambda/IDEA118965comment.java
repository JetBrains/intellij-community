import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class IDEA118965 {
  {
    Stream<String> words = Arrays.asList("one", "one", "two").stream();
    List<Map.Entry<String,Integer>> res = words.collect(Collectors.toMap(w -> w, w -> 1, (a, b) -> a + b))
      .entrySet().stream().filter(e -> e.getValue() > 1).collect(Collectors.toList());
  }
}
