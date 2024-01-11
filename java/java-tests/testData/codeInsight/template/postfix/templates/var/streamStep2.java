import java.util.List;

public class Foo {
  void test(List<String> list) {
    list.stream().anyMatch(s -> s.trim().var<caret>.isEmpty())
  }
}