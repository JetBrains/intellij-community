// "Fix all 'Stream API call chain can be replaced with loop' problems in file" "true"

import java.util.*;

import static java.util.Arrays.asList;

public class Main {
  public static boolean test(List<List<String>> list) {
      for (List<String> x: list) {
          if (x != null) {
              for (String s: x) {
                  if (!s.startsWith("a")) {
                      return false;
                  }
              }
          }
      }
      return true;
  }

  private static boolean testEqEq(List<String> list) {
      for (String s: list) {
          if (s.trim() != s.toLowerCase()) {
              return false;
          }
      }
      return true;
  }

  public static void testIf(List<List<String>> list) {
      boolean b = true;
      OUTER:
      for (List<String> x: list) {
          if (x != null) {
              for (String s: x) {
                  if (!s.startsWith("a")) {
                      b = false;
                      break OUTER;
                  }
              }
          }
      }
      if(b) {
      System.out.println("ok");
    }
  }

  boolean testNot(String... strings) {
      for (String s: strings) {
          if (s != null) {
              if (!s.startsWith("xyz")) {
                  return true;
              }
          }
      }
      return false;
  }

  public void testVar(List<Integer> list) {
      boolean x = false;
      for (Integer i: list) {
          if (i <= 2) {
              x = true;
              break;
          }
      }
      if(x) {
      System.out.println("found");
    }
  }

  public static void main(String[] args) {
    System.out.println(test(asList(asList(), asList("a"), asList("b", "c"))));
    System.out.println(test(asList(asList(), asList("d"), asList("b", "c"))));
    System.out.println(testEqEq(Arrays.asList("a", "b", "c")));
    System.out.println(testIf(asList(asList(), asList("a"), asList("b", "c"))));
    System.out.println(testIf(asList(asList(), asList("d"), asList("b", "c"))));
  }
}