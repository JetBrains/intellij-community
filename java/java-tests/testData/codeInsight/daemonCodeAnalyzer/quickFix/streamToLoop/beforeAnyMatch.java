// "Fix all 'Stream API call chain can be replaced with loop' problems in file" "true"

import java.util.*;

import static java.util.Arrays.asList;

public class Main {
  public static boolean test(List<List<String>> list) {
    return list.stream().filter(x -> x != null).flatMap(x -> x.stream()).anyMat<caret>ch(x -> x.startsWith("a"));
  }

  String testTernary(String[] strings) {
    return Arrays.stream(strings).filter(Objects::nonNull).anyMatch(s -> !s.startsWith("xyz")) ? "s" : null;
  }

  public static void main(String[] args) {
    System.out.println(test(asList(asList(), asList("a"), asList("b", "c"))));
    System.out.println(test(asList(asList(), asList("d"), asList("b", "c"))));
  }
}