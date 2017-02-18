// "Replace Stream API chain with loop" "true"

import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

public class Main {
  private static long test(List<Predicate<String>> predicates, List<String> strings) {
    return predicates.stream().filter(pred -> pred != null)
      .flatMap(p -> strings.stream().filter(p)).co<caret>unt();
  }

  public static void main(String[] args) {
    System.out.println(test(
      Arrays.asList(String::isEmpty, s -> s.length() > 3),
      Arrays.asList("", "a", "abcd", "xyz")
    ));
  }
}