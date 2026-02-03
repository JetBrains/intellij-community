// "Fix all 'Loop can be collapsed with Stream API' problems in file" "true"
import java.util.List;
import java.util.ArrayList;
import java.util.stream.Collectors;

class X {
  void test(List<Object> list) {
    List<String> result = list.stream().filter(o -> o instanceof String).map(o -> (String) o).collect(Collectors.toList());
  }
}