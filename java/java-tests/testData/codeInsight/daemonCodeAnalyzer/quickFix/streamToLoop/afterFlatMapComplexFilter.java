// "Replace Stream API chain with loop" "true"

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class Main {
  public static void test(List<String> list) {
      List<Integer> result = new ArrayList<>();
      for (String x : list) {
          if (x != null) {
              Predicate<Integer> predicate = Predicate.isEqual(x.length());
              for (int i = 0; i < 10; i++) {
                  Integer integer = i;
                  if (predicate.test(integer)) {
                      result.add(integer);
                  }
              }
          }
      }
      System.out.println(result);
  }

  public static void main(String[] args) {
    test(Arrays.asList("a", "bbbb", "cccccccccc", "dd", ""));
  }
}
