// "Replace Stream API chain with loop" "true"

import java.util.*;
import java.util.stream.Collectors;

public class Main {
  private static TreeMap<Integer, LinkedHashSet<String>> getMap(List<String> strings) {
    return strings.stream().coll<caret>ect(
      Collectors.groupingBy(String::length, () -> new TreeMap<>(Comparator.reverseOrder()), Collectors.toCollection(LinkedHashSet::new)));
  }

  public static void main(String[] args) {
    System.out.println(getMap(Arrays.asList("a", "bbb", "cccc", "dddd", "ee", "e")));
  }
}
