
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;

class Test {
  private void foo(Function<? super Integer, Stream<Integer>> first) {
    Function<? super Integer, Object> second = first.andThen(s -> s.map(null));
  }


  private final Function<? super Integer, ? extends Stream<? extends Integer>> first =
    response -> Arrays.asList(response).stream().map(i -> i++);

  private final Function<? super Integer, ? extends Stream<Map<String, Integer>>> second = first.andThen(
    mid -> mid.map(i -> {
      Map<String, Integer> map = new HashMap<>();
      map.put("key", i);
      return map;
    }));
}