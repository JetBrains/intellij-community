// "Replace Stream API chain with loop" "true"

import java.util.PrimitiveIterator;
import java.util.Random;
import java.util.stream.IntStream;

public class Test {
  static void test() {
      for (int n = 1; n < 100; n++) {
          if (n > 20) {
              Integer integer = n;
              for (PrimitiveIterator.OfDouble it = new Random(integer).doubles(integer).iterator(); it.hasNext(); ) {
                  double v = it.next();
                  if (v < 0.01) {
                      System.out.println(v);
                  }
              }
          }
      }
  }

  public static void main(String[] args) {
    test();
  }
}