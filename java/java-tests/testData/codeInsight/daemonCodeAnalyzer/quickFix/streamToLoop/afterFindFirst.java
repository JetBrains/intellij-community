// "Fix all 'Stream API call chain can be replaced with loop' problems in file" "true"

import java.util.List;
import java.util.Optional;

import static java.util.Arrays.asList;

public class Main {
  public static Optional<String> testSimple(List<String> list) {
      for (String s: list) {
          return Optional.of(s);
      }
      return Optional.empty();
  }

  public void testAssign(List<String> list) {
      String res = "";
      for (String s: list) {
          String trim = s.trim();
          if (!trim.isEmpty()) {
              res = trim;
              break;
          }
      }
      System.out.println(res);
  }

  public void testAssignFinal(List<String> list) {
      String res = "";
      for (String s: list) {
          String trim = s.trim();
          if (!trim.isEmpty()) {
              res = trim;
              break;
          }
      }
      System.out.println(res);
  }

  public static Optional<String> testFlatMap(List<List<String>> list) {
      for (List<String> x: list) {
          if (x != null) {
              for (String s: x) {
                  return Optional.of(s);
              }
          }
      }
      return Optional.empty();
  }

  public static String testFlatMapLabelConflict(List<List<String>> list) {
    String res = null;
    OUTER:
    for(int i=0; i<10; i++) {
        String found = "";
        OUTER1:
        for (List<String> x: list) {
            if (x != null) {
                for (String s: x) {
                    found = s;
                    break OUTER1;
                }
            }
        }
        res = found;
    }
    return res;
  }

  public static void main(String[] args) {
    System.out.println(testSimple(asList("a", "b", "c")));
    System.out.println(testFlatMap(asList(asList(), asList("a"), asList("b", "c"))));
    System.out.println(testFlatMapLabelConflict(asList(asList(), asList("a"), asList("b", "c"))));
  }
}