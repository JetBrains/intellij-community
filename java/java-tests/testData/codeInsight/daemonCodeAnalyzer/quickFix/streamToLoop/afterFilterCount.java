// "Fix all 'Stream API call chain can be replaced with loop' problems in file" "true"

import java.util.Arrays;

public class Main {
  private static long countInRange(int... input) {
      long count = 0L;
      for (int x: input) {
          if (x > 0) {
              if (x < 10) {
                  count++;
              }
          }
      }
      return count;
  }

  private static long countInRange1(int... input) {
    int x = 1;
      long count = 0L;
      for (int y: input) {
          if (y > 0) {
              if (y < 10) {
                  count++;
              }
          }
      }
      return count;
  }

  private static long countInRange2(int... input) {
    int x = 1;
    int y = 2;
      long count = 0L;
      for (int i: input) {
          if (i > 0) {
              if (i < 10) {
                  count++;
              }
          }
      }
      return count;
  }

  private static long countInRange3(int... input) {
    int x = 1;
    int y = 2;
    int i = 3;
      long count = 0L;
      for (int x1: input) {
          if (x1 > 0) {
              if (x1 < 10) {
                  count++;
              }
          }
      }
      return count;
  }

  private static long countInRange4(int... input) {
    int x = 1;
    int i = 3;
      long result = 0L;
      for (int count: input) {
          if (count > 0) {
              if (count < 10) {
                  result++;
              }
          }
      }
      return result;
  }

  public static void main(String[] args) {
    System.out.println(countInRange(1, 2, 3, -1, -2));
    System.out.println(countInRange1(1, 2, 3, -1, -2));
    System.out.println(countInRange2(1, 2, 3, -1, -2));
    System.out.println(countInRange3(1, 2, 3, -1, -2));
    System.out.println(countInRange4(1, 2, 3, -1, -2));
  }
}