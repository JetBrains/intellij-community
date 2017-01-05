// "Replace Stream API chain with loop" "true"

import java.util.Optional;
import java.util.stream.Stream;

public class Main {
  private static Optional<Integer> test(Integer... numbers) {
    return Stream.of(numbers).redu<caret>ce((a, b) -> a*b);
  }

  public static void main(String[] args) {
    System.out.println(test(1,2,3,4));
  }
}