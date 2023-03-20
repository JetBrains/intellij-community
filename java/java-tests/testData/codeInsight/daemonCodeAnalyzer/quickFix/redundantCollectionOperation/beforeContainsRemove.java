// "Remove 'contains()' check" "true-preview"
import java.util.List;

class Test {
  void test(List<String> list, String key) {
    if(list.co<caret>ntains(/*contains!!!*/key)) {
      list.remove(/*remove!!!*/key);
    }
  }
}