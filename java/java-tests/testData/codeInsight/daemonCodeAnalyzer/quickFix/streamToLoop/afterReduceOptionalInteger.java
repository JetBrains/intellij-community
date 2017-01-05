// "Replace Stream API chain with loop" "true"

import java.util.Optional;
import java.util.stream.Stream;

public class Main {
  private static Optional<Integer> test(Integer... numbers) {
      boolean seen = false;
      Integer acc = null;
      for (Integer number : numbers) {
          if (!seen) {
              seen = true;
              acc = number;
          } else {
              acc = acc * number;
          }
      }
      return seen ? Optional.of(acc) : Optional.empty();
  }

  public static void main(String[] args) {
    System.out.println(test(1,2,3,4));
  }
}