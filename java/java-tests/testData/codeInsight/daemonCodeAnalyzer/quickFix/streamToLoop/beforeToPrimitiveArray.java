// "Replace Stream API chain with loop" "true"

import java.util.Arrays;
import java.util.stream.IntStream;

public class Main {
  private static int[] test() {
    return IntStream.range(-100, 100).filter(x -> x > 0).toArr<caret>ay();
  }

  public static void main(String[] args) {
    System.out.println(Arrays.toString(test()));
  }
}