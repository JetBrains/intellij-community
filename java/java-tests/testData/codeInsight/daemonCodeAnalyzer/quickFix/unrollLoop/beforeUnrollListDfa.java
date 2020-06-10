// "Unroll loop" "true"
import java.util.List;

class X {
  void testList(List<String> list) {
    if (list.size() == 2) {
      <caret>for (String s : list) {
        System.out.println(s);
      }
    }
  }
}