// "Replace Stream API chain with loop" "true"

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class Main {
  public static void test(List<String> strings) {
      Map<Integer, DoubleSummaryStatistics> map = new HashMap<>();
      for (String string : strings) {
          if (string != null) {
              map.computeIfAbsent(string.length(), k -> new DoubleSummaryStatistics()).accept(string.length());
          }
      }
      System.out.println(map);
  }

  public static void main(String[] args) {
    test(Arrays.asList("a", "bbb", "cccc", "dddd"));
  }
}
