import java.util.List;

public class Foo {
  void test(List<String> list) {
      list.stream().map(String::trim).anyMatch(<caret>trim -> trim.isEmpty())
  }
}