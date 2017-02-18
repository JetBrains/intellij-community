// "Replace Stream API chain with loop" "true"

import java.util.Arrays;
import java.util.List;
import java.util.function.IntSupplier;

public class Main {
  public static int test(List<String> strings, IntSupplier supplier) {
      boolean seen = false;
      int best = 0;
      for (String string : strings) {
          int length = string.length();
          if (!seen || length < best) {
              seen = true;
              best = length;
          }
      }
      return seen ? best : supplier.getAsInt();
  }

  public static void main(String[] args) {
    System.out.println(test(Arrays.asList(), () -> -1));
    System.out.println(test(Arrays.asList("a", "bbb", "cc", "d"), () -> 2));
  }
}