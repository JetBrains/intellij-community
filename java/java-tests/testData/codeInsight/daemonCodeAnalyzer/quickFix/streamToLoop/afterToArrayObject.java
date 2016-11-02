// "Replace Stream API chain with loop" "true"

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Main {
  private static Object[] test(int[] numbers) {
      List<Integer> list = new ArrayList<>();
      for (int number : numbers) {
          Integer integer = number;
          list.add(integer);
      }
      return list.toArray();
  }

  public static void main(String[] args) {
    System.out.println(Arrays.asList(test(new int[] {1,2,3})));
  }
}