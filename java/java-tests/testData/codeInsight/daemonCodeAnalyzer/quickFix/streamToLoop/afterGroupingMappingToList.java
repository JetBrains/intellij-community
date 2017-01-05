// "Replace Stream API chain with loop" "true"

import java.util.*;
import java.util.stream.Collectors;

public class Main {
  private static TreeMap<Integer, List<Integer>> getMap(List<String> strings) {
      TreeMap<Integer, List<Integer>> map = new TreeMap<>(Comparator.reverseOrder());
      for (String string : strings) {
          Integer len = string.length();
          Integer integer = len * 2;
          map.computeIfAbsent(string.length(), k -> new ArrayList<>()).add(integer);
      }
      return map;
  }

  public static void main(String[] args) {
    System.out.println(getMap(Arrays.asList("a", "bbb", "cccc", "dddd", "ee", "e")));
  }
}
