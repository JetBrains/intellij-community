// "Replace Stream API chain with loop" "true"

import java.util.OptionalDouble;
import java.util.stream.LongStream;

public class Main {
  private static OptionalDouble test(long... numbers) {
      double sum = 0;
      long count = 0;
      for (long x : numbers) {
          if (x > 0) {
              double v = x;
              sum += v;
              count++;
          }
      }
      return (count == 0 ? OptionalDouble.empty() : OptionalDouble.of(sum / count));
  }

  public static void main(String[] args) {
    System.out.println(test(-1,-2,-3, Long.MAX_VALUE, Long.MAX_VALUE));
  }
}