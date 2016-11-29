// "Replace Stream API chain with loop" "true"

import java.util.*;
import java.util.stream.Collectors;

public class Main {
  public static Map<Integer, String> test(List<String> strings) {
      Map<Integer, String> map = new HashMap<>();
      for (String str : strings) {
          if (map.put(str.length(), str) != null) {
              throw new IllegalStateException("Duplicate key");
          }
      }
      return map;
  }

  public static void main(String[] args) {
    System.out.println(test(Arrays.asList()));
    System.out.println(test(Arrays.asList("a", "bbb", "cc", "d", "eee", "")));
  }
}