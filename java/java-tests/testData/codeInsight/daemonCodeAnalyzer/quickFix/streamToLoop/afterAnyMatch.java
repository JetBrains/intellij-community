// "Fix all 'Stream API call chain can be replaced with loop' problems in file" "true"

import java.util.*;

import static java.util.Arrays.asList;

public class Main {
  public static boolean test(List<List<String>> list) {
      for (List<String> x: list) {
          if (x != null) {
              for (String s: x) {
                  if (s.startsWith("a")) {
                      return true;
                  }
              }
          }
      }
      return false;
  }

  String testTernary(String[] strings) {
      for (String s: strings) {
          if (s != null) {
              if (!s.startsWith("xyz")) {
                  return "s";
              }
          }
      }
      return null;
  }

  public static void main(String[] args) {
    System.out.println(test(asList(asList(), asList("a"), asList("b", "c"))));
    System.out.println(test(asList(asList(), asList("d"), asList("b", "c"))));
  }
}