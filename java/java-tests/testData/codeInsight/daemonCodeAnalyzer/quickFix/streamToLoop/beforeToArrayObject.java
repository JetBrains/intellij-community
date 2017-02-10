// "Replace Stream API chain with loop" "true"

import java.util.Arrays;

public class Main {
  private static Object[] test(int[] numbers) {
    return Arrays.stream(numbers).boxed().toArr<caret>ay();
  }

  public static void main(String[] args) {
    System.out.println(Arrays.asList(test(new int[] {1,2,3})));
  }
}