import java.util.List;

public class Foo {
  void test(List<String> list) {
    list.stream().anyMatch(s -> {
        String <caret>trim = s.trim();
        return trim.isEmpty();
    })
  }
}