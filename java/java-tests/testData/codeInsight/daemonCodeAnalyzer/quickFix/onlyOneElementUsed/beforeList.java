// "Replace with '"a"'" "true-preview"
import java.util.List;

class Test {
  void test() {
    String s = List.of("a", "b", "c").<caret>get(0);
  }
}