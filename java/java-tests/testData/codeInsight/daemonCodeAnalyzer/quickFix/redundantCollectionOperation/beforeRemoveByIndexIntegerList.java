// "Use removal by object" "true"
import java.util.List;

class Test {
  void test(List<Integer> list, int key) {
    int idx = list.indexOf(key);
    list.r<caret>emove(idx);
  }
}