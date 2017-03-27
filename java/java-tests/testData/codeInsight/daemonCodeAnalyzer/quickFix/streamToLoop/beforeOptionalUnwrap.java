// "Fix all 'Stream API call chain can be replaced with loop' problems in file" "true"

import java.util.*;

public class Main {
  private static String testOrElse(List<String> list) {
    if (list == null) return null;
    else return list.stream().filter(str -> str.contains("x")).fin<caret>dFirst().orElse(null);
  }

  private static String testOrElseGet(List<String> list) {
    if (list == null) return null;
    else return list.stream().filter(str -> str.contains("x")).findFirst().orElseGet(() -> null);
  }

  private static void testIfPresent(List<String> list) {
    list.stream().filter(str -> str.contains("x")).findFirst().ifPresent(System.out::println);
  }

  public static boolean testIsPresent(List<List<String>> list) {
    return list.stream().filter(Objects::nonNull).flatMap(List::stream).findAny().isPresent();
  }

  static String testIsPresentNotTernary(List<List<String>> strings) {
    return !strings.stream().filter(Objects::nonNull).flatMap(List::stream).findFirst().isPresent() ? "xyz" : "abc";
  }

  public static void main(String[] args) {
    System.out.println(testOrElse(Arrays.asList("a", "b", "syz")));
    System.out.println(testOrElseGet(Arrays.asList("a", "b", "syz")));
    testIfPresent(Arrays.asList("a", "b", "syz"));
    System.out.println(testIsPresent(asList(asList(), asList("a"), asList("b", "c"))));
    System.out.println(testIsPresentNotTernary(asList(asList(), asList("a"), asList("b", "c"))));
  }
}