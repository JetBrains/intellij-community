// "Replace Stream API chain with loop" "true"

import java.util.*;
import java.util.stream.Collectors;

public class Main {
  private static TreeMap<Integer, List<Integer>> getMap(List<String> strings) {
    return strings.stream().colle<caret>ct(
      Collectors.groupingBy(String::length, () -> new TreeMap<>(Comparator.reverseOrder()),
                            Collectors.mapping(String::length, Collectors.mapping(len -> len*2, Collectors.toList()))));
  }

  public static void main(String[] args) {
    System.out.println(getMap(Arrays.asList("a", "bbb", "cccc", "dddd", "ee", "e")));
  }
}
