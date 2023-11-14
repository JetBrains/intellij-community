// "Fix all 'Stream API call chain can be simplified' problems in file" "true"
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

public class Main {

  void testSimple(Map<String, Integer> map) {
    Iterable<String> it = map.keySet().stream().toLi<caret>st();
    for (String s : it) {
      System.out.println(s);
    }
  }

  void testCollection(Map<String, Integer> map) {
    Collection<String> it = map.keySet().stream().toList();
    for (String s : it) {
      System.out.println(s);
    }
  }

  Collection<String> testReturnVariable(Map<String, Integer> map) {
    Collection<String> it = map.keySet().stream().toList();
    for (String s : it) {
      System.out.println(s);
    }
    return it;
  }

  void testPassVariableAsParameter(Map<String, Integer> map) {
    Collection<String> it = map.keySet().stream().toList();
    for (String s : it) {
      System.out.println(s);
    }
    sink(it);
  }

  private void sink(Collection<String> it) {

  }
}