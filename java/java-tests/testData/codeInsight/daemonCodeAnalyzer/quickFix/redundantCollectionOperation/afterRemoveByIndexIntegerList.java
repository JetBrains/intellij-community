// "Use removal by object" "true"
import java.util.List;

class Test {
  void test(List<Integer> list, int key) {
      list.remove((Integer) key);
  }
}