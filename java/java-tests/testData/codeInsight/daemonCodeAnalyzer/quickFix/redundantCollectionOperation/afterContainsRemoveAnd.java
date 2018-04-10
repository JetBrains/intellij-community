// "Remove the 'contains' check" "true"
import java.util.List;

class Test {
  void test(List<String> list, String key) {
    if(key != null) {
      list.remove(key);
    }
  }
}