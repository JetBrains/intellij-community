// "Replace Stream API chain with loop" "true"

import java.util.Arrays;
import java.util.List;

public class Main {
  private static Integer test(List<Integer> numbers) {
      Integer acc = 0;
      for (Integer number : numbers) {
          acc = Math.max(acc, number);
      }
      return acc;
  }

  public static void main(String[] args) {
    test(Arrays.asList("a", "b", "xyz"));
  }
}