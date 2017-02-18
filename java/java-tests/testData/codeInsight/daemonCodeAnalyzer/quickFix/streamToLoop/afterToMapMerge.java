// "Replace Stream API chain with loop" "true"

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class Main {
  public static Map<Integer, String> test(List<String> strings) {
      Map<Integer, String> map = new HashMap<>();
      for (String s : strings) {
          if (!s.isEmpty()) {
              map.merge(s.length(), s, String::concat);
          }
      }
      return map;
  }

  public static void main(String[] args) {
    System.out.println(test(Arrays.asList()));
    System.out.println(test(Arrays.asList("a", "bbb", "cc", "d", "eee", "")));
  }
}