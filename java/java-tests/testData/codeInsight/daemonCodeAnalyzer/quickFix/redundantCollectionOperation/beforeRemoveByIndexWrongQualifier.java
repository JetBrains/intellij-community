// "Use removal by object" "false"
import java.util.List;

class Test {
  void test(List<String> list, List<String> list2, String key) {
    int idx = list.indexOf(key);
    list2.r<caret>emove(idx);
  }
}