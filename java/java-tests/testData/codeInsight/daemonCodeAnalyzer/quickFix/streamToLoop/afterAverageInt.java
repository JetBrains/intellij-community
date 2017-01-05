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
      return count > 0 ? OptionalDouble.of((double) sum / count) : OptionalDouble.empty();
  }

  public static void main(String[] args) {
    System.out.println(test(-1,-2,-3));
  }
}