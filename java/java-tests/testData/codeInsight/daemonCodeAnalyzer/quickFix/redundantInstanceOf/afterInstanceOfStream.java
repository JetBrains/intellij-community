// "Replace with a null check" "true"
import java.util.Objects;
import java.util.stream.Stream;

class Test {
  void test(Stream<String> s) {
    s.filter(Objects::nonNull)
      .forEach(System.out::println);
  }
}