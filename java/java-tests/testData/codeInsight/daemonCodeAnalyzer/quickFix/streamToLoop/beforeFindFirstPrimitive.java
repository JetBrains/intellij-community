// "Fix all 'Stream API call chain can be replaced with loop' problems in file" "true"

import java.util.*;
import java.util.stream.*;

public class Main {

  private static OptionalInt test() {
    return IntStream.range(0, 100).filter(x -> x > 50).findFir<caret>st();
  }

  private static int test2() {
    return IntStream.range(0, 100).filter(x -> x > 50).findFirst().orElse(0);
  }

  private static int test3() {
    return IntStream.range(0, 100).filter(x -> x > 50).findFirst().orElseGet(() -> Math.abs(-1));
  }

  private static int test4() {
    int res = IntStream.range(0, 100).filter(x -> x > 50).findFirst().orElseGet(() -> Math.abs(-1));
    return res;
  }

  public static void main(String[] args) {
    System.out.println(test());
    System.out.println(test2());
    System.out.println(test3());
    System.out.println(test4());
  }
}