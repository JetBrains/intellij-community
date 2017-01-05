// "Replace Stream API chain with loop" "true"

import java.util.Arrays;
import java.util.stream.IntStream;

public class Main {
  private static int[] test() {
      int[] arr = new int[10];
      int count = 0;
      for (int x = -100; x < 100; x++) {
          if (x > 0) {
              if (arr.length == count) arr = Arrays.copyOf(arr, count * 2);
              arr[count++] = x;
          }
      }
      arr = Arrays.copyOfRange(arr, 0, count);
      return arr;
  }

  public static void main(String[] args) {
    System.out.println(Arrays.toString(test()));
  }
}