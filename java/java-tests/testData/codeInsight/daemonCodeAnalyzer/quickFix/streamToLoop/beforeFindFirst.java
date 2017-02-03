// "Fix all 'Stream API call chain can be replaced with loop' problems in file" "true"

import java.util.List;
import java.util.Optional;

import static java.util.Arrays.asList;

public class Main {
  public static Optional<String> testSimple(List<String> list) {
    return list.stream().findFi<caret>rst();
  }

  public void testAssign(List<String> list) {
    String res = list.stream().map(String::trim).filter(trim -> !trim.isEmpty()).findFirst().orElse("");
    System.out.println(res);
  }

  public void testAssignFinal(List<String> list) {
    final String res = list.stream().map(String::trim).filter(trim -> !trim.isEmpty()).findFirst().orElse("");
    System.out.println(res);
  }

  public static Optional<String> testFlatMap(List<List<String>> list) {
    return list.stream().filter(x -> x != null).flatMap(x -> x.stream()).findAny();
  }

  public static String testFlatMapLabelConflict(List<List<String>> list) {
    String res = null;
    OUTER:
    for(int i=0; i<10; i++) {
      res = list.stream().filter(x -> x != null).flatMap(x -> x.stream()).findAny().orElse("");
    }
    return res;
  }

  public static void main(String[] args) {
    System.out.println(testSimple(asList("a", "b", "c")));
    System.out.println(testFlatMap(asList(asList(), asList("a"), asList("b", "c"))));
    System.out.println(testFlatMapLabelConflict(asList(asList(), asList("a"), asList("b", "c"))));
  }
}