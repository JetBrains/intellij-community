// "Fix all 'Stream API call chain can be replaced with loop' problems in file" "true"

import java.util.Arrays;

public class Main {
  private static long countInRange(int... input) {
    return Arrays.stream(input).filter(x -> x > 0).filter(y -> {
      return y < 10;
    }).co<caret>unt();
  }

  private static long countInRange1(int... input) {
    int x = 1;
    return Arrays.stream(input).filter(x -> x > 0).filter(y -> y < 10).count();
  }

  private static long countInRange2(int... input) {
    int x = 1;
    int y = 2;
    return Arrays.stream(input).filter(x -> x > 0).filter(y -> y < 10).count();
  }

  private static long countInRange3(int... input) {
    int x = 1;
    int y = 2;
    int i = 3;
    return Arrays.stream(input).filter(x -> x > 0).filter(y -> y < 10).count();
  }

  private static long countInRange4(int... input) {
    int x = 1;
    int i = 3;
    return Arrays.stream(input).filter(x -> x > 0).filter(count -> count < 10).count();
  }

  public static void main(String[] args) {
    System.out.println(countInRange(1, 2, 3, -1, -2));
    System.out.println(countInRange1(1, 2, 3, -1, -2));
    System.out.println(countInRange2(1, 2, 3, -1, -2));
    System.out.println(countInRange3(1, 2, 3, -1, -2));
    System.out.println(countInRange4(1, 2, 3, -1, -2));
  }
}