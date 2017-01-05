// "Replace Stream API chain with loop" "true"

import java.util.OptionalDouble;
import java.util.stream.LongStream;

public class Main {
  private static OptionalDouble test(long... numbers) {
    return LongStream.of(numbers).filter(x -> x > 0).avera<caret>ge();
  }

  public static void main(String[] args) {
    System.out.println(test(-1,-2,-3, Long.MAX_VALUE, Long.MAX_VALUE));
  }
}