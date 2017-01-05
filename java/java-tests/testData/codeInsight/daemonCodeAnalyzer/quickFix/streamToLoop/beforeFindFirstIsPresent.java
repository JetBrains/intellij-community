// "Replace Stream API chain with loop" "true"

import java.util.*;

import static java.util.Arrays.asList;

public class Main {
  public static boolean test(List<List<String>> list) {
    return list.stream().filter(Objects::nonNull).flatMap(List::stream).findA<caret>ny().isPresent();
  }

  public static void main(String[] args) {
    System.out.println(test(asList(asList(), asList("a"), asList("b", "c"))));
  }
}