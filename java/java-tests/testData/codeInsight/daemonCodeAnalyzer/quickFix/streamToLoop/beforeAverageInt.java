// "Replace Stream API chain with loop" "true"

import java.util.OptionalDouble;
import java.util.stream.IntStream;

public class Main {
  private static OptionalDouble test(int... numbers) {
    return IntStream.of(numbers).filter(x -> x > 0).aver<caret>age();
  }

  public static void main(String[] args) {
    System.out.println(test(-1,-2,-3));
  }
}