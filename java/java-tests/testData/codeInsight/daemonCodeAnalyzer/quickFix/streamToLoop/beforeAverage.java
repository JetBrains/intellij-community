// "Fix all 'Stream API call chain can be replaced with loop' problems in file" "true"

import java.util.OptionalDouble;
import java.util.stream.*;

public class Main {
  private static OptionalDouble testDouble(long... numbers) {
    return LongStream.of(numbers).filter(x -> x > 0).asDoubleStream().avera<caret>ge();
  }

  private static OptionalDouble testInt(int... numbers) {
    return IntStream.of(numbers).filter(x -> x > 0).average();
  }

  private static OptionalDouble testLong(long... numbers) {
    return LongStream.of(numbers).filter(x -> x > 0).average();
  }

  public static void main(String[] args) {
    System.out.println(testDouble(-1,-2,-3, Long.MAX_VALUE, Long.MAX_VALUE));
    System.out.println(testInt(-1,-2,-3));
    System.out.println(testLong(-1,-2,-3, Long.MAX_VALUE, Long.MAX_VALUE));
  }
}