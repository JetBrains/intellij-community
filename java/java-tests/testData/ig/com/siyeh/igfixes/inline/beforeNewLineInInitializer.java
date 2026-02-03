// "Inline variable" "true-preview"
import java.util.List;

public class Demo {
  List<String> test(List<String> list) {
    List<String> re<caret>sult =
      list.stream()
        .map(String::toUpperCase)
        .map(String::trim)
        .toList();
    return result;
  }
}