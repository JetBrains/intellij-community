// "Replace Stream API chain with loop" "true"

import java.util.SplittableRandom;
import java.util.stream.Stream;

public class Main {
  public static void main(String[] args) {
      Integer acc = 0;
      SplittableRandom splittableRandom = new SplittableRandom(1);
      for (long count = 100; count > 0; count--) {
          Integer integer = 500;
          acc = splittableRandom.nextInt(acc, integer);
      }
      int n1 = acc;
    System.out.println(n1);
  }
}