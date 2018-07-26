// "Fix all 'Stream API call chain can be replaced with loop' problems in file" "true"

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import static java.util.stream.Collectors.*;

public class Main {
  static final Predicate<String> nonEmpty = s -> s != null && !s.isEmpty();

  private static long testFunctionInField(List<String> strings) {
      long count = 0L;
      for (String string : strings) {
          if (nonEmpty.test(string)) {
              count++;
          }
      }
      return count;
  }

  private Set<Identifier> test(Set<Identifier> identifiers) {
      Set<Identifier> set = new HashSet<>();
      Predicate<? super Identifier> predicate = isReady(identifiers);
      for (Identifier identifier : identifiers) {
          if (predicate.test(identifier)) {
              set.add(identifier);
          }
      }
      return set;
  }

  private Predicate<? super Identifier> isReady(Set<Identifier> identifiers) {
    return null;
  }

  private static class Identifier {
  }

  private static long testStreamOfFunctions(List<Predicate<String>> predicates, List<String> strings) {
      long count = 0L;
      for (Predicate<String> pred : predicates) {
          if (pred != null) {
              for (String string : strings) {
                  if (pred.test(string)) {
                      count++;
                  }
              }
          }
      }
      return count;
  }

  public static void main(String[] args) {
    System.out.println(testStreamOfFunctions(
      Arrays.asList(String::isEmpty, s -> s.length() > 3),
      Arrays.asList("", "a", "abcd", "xyz")
    ));
  }
}