// "Replace Stream API chain with loop" "true"

import java.util.*;
import java.util.stream.Collectors;

public class Main {
  public static TreeMap<Integer, String> test(List<String> strings) {
      TreeMap<Integer, String> map = new TreeMap<>();
      for (String s1 : strings) {
          if (!s1.isEmpty()) {
              map.put(s1.length(), s1.trim());
          }
      }
      return map;
  }

  public static void main(String[] args) {
    System.out.println(test(Arrays.asList()));
    System.out.println(test(Arrays.asList("a", "bbb", "cc", "d", "eee", "")));
  }
}