// "Replace Stream API chain with loop" "true"

import java.util.*;

public class Main {

  private static long test(Map<String, List<String>> strings) {
      long count = 0L;
      for (Map.Entry<String, List<String>> e : strings.entrySet()) {
          if (!e.getKey().isEmpty()) {
              String sInner = e.getKey();
              for (String s : e.getValue()) {
                  if (sInner.equals(s)) {
                      count++;
                  }
              }
          }
      }
      return count;
  }

  public static void main(String[] args) {
    Map<String, List<String>> map = new HashMap<>();
    map.put("", Arrays.asList("", "a", "b"));
    map.put("a", Arrays.asList("", "a", "b", "a"));
    map.put("b", Arrays.asList("", "a", "b"));
    map.put("c", Arrays.asList("", "a", "b"));
    System.out.println(test(map));
  }
}