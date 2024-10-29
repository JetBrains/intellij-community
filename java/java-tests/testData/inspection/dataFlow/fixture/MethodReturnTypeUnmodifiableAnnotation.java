import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Map;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class MethodReturnTypeUnmodifiableAnnotation {
  void test() {
    Collector<String, ?, @NotNull @Unmodifiable Map<@NotNull Integer, @NotNull String>> unmodifiableMap = Collectors.toUnmodifiableMap(Integer::parseInt, xx -> xx);
    Stream.of("1", "2", "3").collect(unmodifiableMap).<warning descr="Immutable object is modified">put</warning>(1, "2");
    var map = Stream.of("1", "2", "3").collect(unmodifiableMap);
    map.<warning descr="Immutable object is modified">put</warning>(1, "3");
  }
}
