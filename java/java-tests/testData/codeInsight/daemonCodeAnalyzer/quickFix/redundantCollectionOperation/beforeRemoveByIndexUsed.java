// "Use removal by object" "false"
import java.util.List;

class Test {
  void test(List<String> list, String key) {
    int idx = list.indexOf(key);
    String x = list.r<caret>emove(idx);
  }
}