// "Replace Stream API chain with loop" "true"

import java.util.Arrays;

public class Main {
  private static long countInRange(int... input) {
    return Arrays.stream(input).filter(x -> x > 0).filter(y -> {
      return y < 10;
    }).co<caret>unt();
  }

  public static void main(String[] args) {
    System.out.println(countInRange(1, 2, 3, -1, -2));
  }
}