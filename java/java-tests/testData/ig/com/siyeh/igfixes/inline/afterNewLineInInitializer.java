// "Inline variable" "true-preview"
import java.util.List;

public class Demo {
  List<String> test(List<String> list) {
    <caret>  return list.stream()
        .map(String::toUpperCase)
        .map(String::trim)
        .toList();
  }
}