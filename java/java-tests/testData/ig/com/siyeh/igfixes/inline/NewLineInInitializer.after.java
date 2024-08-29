import java.util.List;

public class Demo {
  List<String> test(List<String> list) {
      return <caret>list.stream()
        .map(String::toUpperCase)
        .map(String::trim)
        .toList();
  }
}