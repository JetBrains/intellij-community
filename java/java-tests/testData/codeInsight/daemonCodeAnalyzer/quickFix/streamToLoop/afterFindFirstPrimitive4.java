// "Replace Stream API chain with loop" "true"

import java.util.*;
import java.util.stream.*;

public class Main {

  private static int test() {
      OptionalInt found = OptionalInt.empty();
      for (int x = 0; x < 100; x++) {
          if (x > 50) {
              found = OptionalInt.of(x);
              break;
          }
      }
      int res = found.orElseGet(() -> Math.abs(-1));
    return res;
  }

  public static void main(String[] args) {
    System.out.println(test());
  }
}