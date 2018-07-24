// "Fix all 'Stream API call chain can be replaced with loop' problems in file" "true"

import java.util.OptionalDouble;
import java.util.stream.*;

public class Main {
  private static OptionalDouble testDouble(long... numbers) {
      double sum = 0;
      long count = 0;
      for (long x: numbers) {
          if (x > 0) {
              double v = x;
              sum += v;
              count++;
          }
      }
      return count > 0 ? OptionalDouble.of(sum / count) : OptionalDouble.empty();
  }

  private static OptionalDouble testInt(int... numbers) {
      long sum = 0;
      long count = 0;
      for (int x: numbers) {
          if (x > 0) {
              sum += x;
              count++;
          }
      }
      return count > 0 ? OptionalDouble.of((double) sum / count) : OptionalDouble.empty();
  }

  private static OptionalDouble testLong(long... numbers) {
      long sum = 0;
      long count = 0;
      for (long x: numbers) {
          if (x > 0) {
              sum += x;
              count++;
          }
      }
      return count > 0 ? OptionalDouble.of((double) sum / count) : OptionalDouble.empty();
  }

  public static void main(String[] args) {
    System.out.println(testDouble(-1,-2,-3, Long.MAX_VALUE, Long.MAX_VALUE));
    System.out.println(testInt(-1,-2,-3));
    System.out.println(testLong(-1,-2,-3, Long.MAX_VALUE, Long.MAX_VALUE));
  }
}