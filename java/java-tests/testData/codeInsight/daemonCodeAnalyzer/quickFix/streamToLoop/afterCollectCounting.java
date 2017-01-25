// "Replace Stream API chain with loop" "true"

import java.util.*;
import java.util.stream.Collectors;

public class Main {
  public static long test(List<String> strings) {
      long count = 0L;
      for (String s : strings) {
          if (!s.isEmpty()) {
              count++;
          }
      }
      return count;
  }

  public static void main(String[] args) {
    System.out.println(test(Arrays.asList()));
    System.out.println(test(Arrays.asList("a", "bbb", "cc", "d", "eee", "")));
  }
}