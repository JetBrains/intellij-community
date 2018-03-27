// "Remove the 'contains' check" "true"
import java.util.List;

class Test {
  void test(List<String> list, String key) {
    if(list.co<caret>ntains(/*contains!!!*/key)) {
      list.remove(/*remove!!!*/key);
    }
  }
}