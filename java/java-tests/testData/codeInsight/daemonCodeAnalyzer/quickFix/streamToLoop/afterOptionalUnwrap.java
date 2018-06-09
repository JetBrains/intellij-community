// "Fix all 'Stream API call chain can be replaced with loop' problems in file" "true"

import java.util.*;

public class Main {
  private static String testOrElse(List<String> list) {
    if (list == null) return null;
    else {
        for (String str: list) {
            if (str.contains("x")) {
                return str;
            }
        }
        return null;
    }
  }

  private static String testOrElseGet(List<String> list) {
    if (list == null) return null;
    else {
        for (String str: list) {
            if (str.contains("x")) {
                return str;
            }
        }
        return null;
    }
  }

  private static void testIfPresent(List<String> list) {
      for (String str: list) {
          if (str.contains("x")) {
              System.out.println(str);
              break;
          }
      }
  }

  public static boolean testIsPresent(List<List<String>> list) {
      for (List<String> strings: list) {
          if (strings != null) {
              for (String string: strings) {
                  return true;
              }
          }
      }
      return false;
  }

  static String testIsPresentNotTernary(List<List<String>> strings) {
      for (List<String> string: strings) {
          if (string != null) {
              for (String s: string) {
                  return "abc";
              }
          }
      }
      return "xyz";
  }

  public static void main(String[] args) {
    System.out.println(testOrElse(Arrays.asList("a", "b", "syz")));
    System.out.println(testOrElseGet(Arrays.asList("a", "b", "syz")));
    testIfPresent(Arrays.asList("a", "b", "syz"));
    System.out.println(testIsPresent(asList(asList(), asList("a"), asList("b", "c"))));
    System.out.println(testIsPresentNotTernary(asList(asList(), asList("a"), asList("b", "c"))));
  }
}