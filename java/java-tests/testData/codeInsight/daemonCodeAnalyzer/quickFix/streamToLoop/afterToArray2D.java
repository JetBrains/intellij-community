// "Replace Stream API chain with loop" "true"

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Main {
  private static Integer[][] test(int[] numbers) {
      List<Integer[]> list = new ArrayList<>();
      for (int number : numbers) {
          Integer n = number;
          Integer[] integers = new Integer[]{n};
          list.add(integers);
      }
      return list.toArray(new Integer[0][]);
  }

  public static void main(String[] args) {
    System.out.println(Arrays.asList(test(new int[] {1,2,3})));
  }
}