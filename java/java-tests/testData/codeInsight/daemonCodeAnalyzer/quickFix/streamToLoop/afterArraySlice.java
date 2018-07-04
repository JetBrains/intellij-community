// "Replace Stream API chain with loop" "true"

import java.util.Arrays;

public class Test {
  public static void test(String[] arr) {
      int bound = arr.length - 1;
      for (int i = 1; i < bound; i++) {
          String s = arr[i];
          System.out.println(s);
      }
  }
}
