// "Replace Stream API chain with loop" "true-preview"

import java.util.PrimitiveIterator;
import java.util.Random;
import java.util.stream.IntStream;

public class Test {
  static void test() {
      for (int n = 1; n < 100; n++) {
          if (n > 20) {
              Integer i = n;
              for (PrimitiveIterator.OfDouble it = new Random(i).doubles(i).iterator(); it.hasNext(); ) {
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