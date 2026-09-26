// "Set variable type to 'String'" "true-preview"
import java.util.List;

class Demo {
  void test(List<String> list) {
    for (var<caret> s : list) {
    }
  }
}
