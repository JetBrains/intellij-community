// "Replace Stream API chain with loop" "true"

import java.util.Arrays;

public class Test {
  public static void test(String[] arr) {
    Arrays.stream(arr, 1, arr.length - 1).forE<caret>ach(System.out::println);
  }
}
