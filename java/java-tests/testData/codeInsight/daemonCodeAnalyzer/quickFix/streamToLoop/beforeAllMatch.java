// "Fix all 'Stream API call chain can be replaced with loop' problems in file" "true"

import java.util.*;

import static java.util.Arrays.asList;

public class Main {
  public static boolean test(List<List<String>> list) {
    return list.stream().filter(x -> x != null).flatMap(x -> x.stream()).allMat<caret>ch(x -> x.startsWith("a"));
  }

  private static boolean testEqEq(List<String> list) {
    return list.stream().allMatch(s -> s.trim() == s.toLowerCase());
  }

  public static void testIf(List<List<String>> list) {
    if(list.stream().filter(x -> x != null).flatMap(x -> x.stream()).allMatch(x -> x.startsWith("a"))) {
      System.out.println("ok");
    }
  }

  boolean testNot(String... strings) {
    return !Arrays.stream(strings).filter(Objects::nonNull).allMatch(s -> s.startsWith("xyz"));
  }

  public void testVar(List<Integer> list) {
    boolean x = !list.stream().all<caret>Match(i -> i > 2);
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