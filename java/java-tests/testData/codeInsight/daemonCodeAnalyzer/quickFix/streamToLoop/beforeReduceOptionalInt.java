// "Replace Stream API chain with loop" "true"

import java.util.OptionalInt;
import java.util.stream.IntStream;

public class Main {
  private static OptionalInt test() {
    return IntStream.of().redu<caret>ce((a, b) -> a*b);
  }

  public static void main(String[] args) {
    System.out.println(test());
  }
}