// "Replace Stream API chain with loop" "true"

import java.util.Arrays;

public class Main {
  private static Integer[][] test(int[] numbers) {
    return Arrays.stream(numbers).boxed().map(n -> new Integer[] {n}).toArr<caret>ay(Integer[][]::new);
  }

  public static void main(String[] args) {
    System.out.println(Arrays.asList(test(new int[] {1,2,3})));
  }
}