// "Remove 'contains()' check" "true-preview"
import java.util.List;

class Test {
  void test(List<String> list, String key) {
      /*contains!!!*/
      list.remove(/*remove!!!*/key);
  }
}