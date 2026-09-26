// "Fix all 'Loop can be collapsed with Stream API' problems in file" "true"
import java.util.List;
import java.util.ArrayList;

class X {
  void test(List<Object> list) {
    List<String> result = new ArrayList<>();
    f<caret>or (Object o : list) {
      if (o instanceof String s) {
        result.add(s);
      }
    }
  }
}