// "Fix all 'Stream API call chain can be replaced with loop' problems in file" "true"

import java.util.*;
import java.util.stream.Stream;
import java.util.function.Predicate;

import static java.util.Arrays.asList;

public class Main {
  public static boolean test(List<List<String>> list) {
    return list.stream().filter(x -> x != null).flatMap(x -> x.stream()).anyMat<caret>ch(x -> x.startsWith("a"));
  }

  String testTernary(String[] strings) {
    return Arrays.stream(strings).filter(Objects::nonNull).anyMatch(s -> !s.startsWith("xyz")) ? "s" : null;
  }
  
  void predicateNot() {
    boolean res = Stream.of(Optional.of(1), Optional.of(2), Optional.empty())
      .anyMatch(Predicate.not(Optional::isEmpty));
  }

  void predicateNotLambda() {
    boolean res = Stream.of(Optional.of(1), Optional.of(2), Optional.empty())
      .anyMatch(Predicate.not(o -> o.isEmpty()));
  }

  public static void main(String[] args) {
    System.out.println(test(asList(asList(), asList("a"), asList("b", "c"))));
    System.out.println(test(asList(asList(), asList("d"), asList("b", "c"))));
  }
}