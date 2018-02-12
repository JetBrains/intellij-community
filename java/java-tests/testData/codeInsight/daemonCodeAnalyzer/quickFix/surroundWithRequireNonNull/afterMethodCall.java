// "Replace with 'Objects.requireNonNull(s1)'" "true"
import java.util.List;
import java.util.Objects;

class MyClass {
  void test(List<String> list) {
    list.stream().map(s -> s.isEmpty() ? null : s)
      .map(s1 -> Objects.requireNonNull(s1).trim())
      .forEach(System.out::println);
  }
}