// "Fix all 'Stream API call chain can be replaced with loop' problems in file" "true"

import java.util.*;
import java.util.stream.Stream;
import java.util.function.Predicate;

import static java.util.Arrays.asList;

public class Main {
  public static boolean test(List<List<String>> list) {
      for (List<String> x : list) {
          if (x != null) {
              for (String s : x) {
                  if (s.startsWith("a")) {
                      return true;
                  }
              }
          }
      }
      return false;
  }

  String testTernary(String[] strings) {
      for (String s : strings) {
          if (s != null) {
              if (!s.startsWith("xyz")) {
                  return "s";
              }
          }
      }
      return null;
  }
  
  void predicateNot() {
      boolean res = false;
      for (Optional<?> o : asList(Optional.of(1), Optional.of(2), Optional.empty())) {
          if (!o.isEmpty()) {
              res = true;
              break;
          }
      }
  }

  void predicateNotLambda() {
      boolean res = false;
      for (Optional<?> o : asList(Optional.of(1), Optional.of(2), Optional.empty())) {
          if (o.isPresent()) {
              res = true;
              break;
          }
      }
  }

  public static void main(String[] args) {
    System.out.println(test(asList(asList(), asList("a"), asList("b", "c"))));
    System.out.println(test(asList(asList(), asList("d"), asList("b", "c"))));
  }
}