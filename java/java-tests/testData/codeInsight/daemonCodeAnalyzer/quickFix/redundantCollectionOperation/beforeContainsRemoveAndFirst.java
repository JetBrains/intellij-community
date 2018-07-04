// "Remove the 'contains' check" "false"
import java.util.List;

class Test {
  void test(List<String> list, String key) {
    if(list.co<caret>ntains(key) && key != null) {
      list.remove(key);
    }
  }
}