import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class MyClass {
  private static void test() {
    List<String> collect = Arrays.asList("foo", "bar")
      .stream()
      .flatMap(MyClass::mapper)
      .filter(s -> s.startsWith("foo"))
      .collect(Collectors.toList());
  }

  private static Stream<String> mapper(String s) {
    return Stream.of(s);
  }
}
