// "Replace Stream API chain with loop" "true"

import java.util.OptionalDouble;
import java.util.stream.IntStream;

public class Main {
  private static OptionalDouble test(int... numbers) {
      long sum = 0;
      long count = 0;
      for (int x : numbers) {
          if (x > 0) {
              sum += x;
              count++;
          }
      }
      return count == 0 ? OptionalDouble.empty() : OptionalDouble.of((double) sum / count);
  }

  public static void main(String[] args) {
    System.out.println(test(-1,-2,-3));
  }
}