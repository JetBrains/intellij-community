// "Fix all 'Loop can be collapsed with Stream API' problems in file" "true"
import java.util.List;
import java.util.ArrayList;
import java.util.stream.Collectors;

class X {
  void test(List<Object> list) {
    List<Object> result = list.stream().filter(o -> getObject(o) instanceof String s && !s.isEmpty()).collect(Collectors.toList());
  }
  
  native Object getObject(Object obj);
}