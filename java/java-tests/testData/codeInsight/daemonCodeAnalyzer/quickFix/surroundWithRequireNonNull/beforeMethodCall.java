// "Replace with 'Objects.requireNonNull(s1)'" "true"
import java.util.List;

class MyClass {
  void test(List<String> list) {
    list.stream().map(s -> s.isEmpty() ? null : s)
      .map(s1 -> s1.t<caret>rim())
      .forEach(System.out::println);
  }
}