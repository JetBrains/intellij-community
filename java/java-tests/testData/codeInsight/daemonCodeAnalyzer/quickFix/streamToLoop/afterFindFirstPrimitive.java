// "Fix all 'Stream API call chain can be replaced with loop' problems in file" "true"

import java.util.*;
import java.util.stream.*;

public class Main {

  private static OptionalInt test() {
      for (int x = 0; x < 100; x++) {
          if (x > 50) {
              return OptionalInt.of(x);
          }
      }
      return OptionalInt.empty();
  }

  private static int test2() {
      for (int x = 0; x < 100; x++) {
          if (x > 50) {
              return x;
          }
      }
      return 0;
  }

  private static int test3() {
      for (int x = 0; x < 100; x++) {
          if (x > 50) {
              return x;
          }
      }
      return Math.abs(-1);
  }

  private static int test4() {
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
    System.out.println(test2());
    System.out.println(test3());
    System.out.println(test4());
  }
}