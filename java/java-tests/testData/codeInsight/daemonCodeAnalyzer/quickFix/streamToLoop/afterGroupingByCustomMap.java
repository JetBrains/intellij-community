// "Replace Stream API chain with loop" "true"

import java.util.*;
import java.util.stream.Collectors;

public class Main {
  private static TreeMap<Integer, LinkedHashSet<String>> getMap(List<String> strings) {
      TreeMap<Integer, LinkedHashSet<String>> map = new TreeMap<>(Comparator.reverseOrder());
      for (String string : strings) {
          map.computeIfAbsent(string.length(), k -> new LinkedHashSet<>()).add(string);
      }
      return map;
  }

  public static void main(String[] args) {
    System.out.println(getMap(Arrays.asList("a", "bbb", "cccc", "dddd", "ee", "e")));
  }
}
