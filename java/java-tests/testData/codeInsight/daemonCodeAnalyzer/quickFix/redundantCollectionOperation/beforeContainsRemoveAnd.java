// "Remove 'contains()' check" "true-preview"
import java.util.List;

class Test {
  void test(List<String> list, String key) {
    if(key != null && list.co<caret>ntains(key)) {
      list.remove(key);
    }
  }
}