
import java.util.ArrayList;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Collectors;


class Foo {
  private static void foo(final Function<String, ?> compose) {
    Collector<String, ?, ? extends Map<?, ArrayList<String>>> stringMapCollector =
      Collectors.groupingBy(compose, Collector.of(() -> new ArrayList<String>(), null, null, s -> new ArrayList<>(s)));
  }
}