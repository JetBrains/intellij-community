// "Fix all 'Stream API call chain can be replaced with loop' problems in file" "true"

import java.util.List;
import java.util.function.Predicate;

public class Main {
  static Predicate<String> nonEmpty = s -> s != null && !s.isEmpty();

  private static long testFunctionInField(List<String> strings) {
    return strings.stream().filter(nonEmpty).cou<caret>nt();
  }

  private static long testStreamOfFunctions(List<Predicate<String>> predicates, List<String> strings) {
    return predicates.stream().filter(pred -> pred != null)
             .flatMap(p -> strings.stream().filter(p)).count();
  }

  public static void main(String[] args) {
    System.out.println(testStreamOfFunctions(
      Arrays.asList(String::isEmpty, s -> s.length() > 3),
      Arrays.asList("", "a", "abcd", "xyz")
    ));
  }
}