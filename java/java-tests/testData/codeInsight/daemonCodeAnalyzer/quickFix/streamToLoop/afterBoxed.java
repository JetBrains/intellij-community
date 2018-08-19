// "Replace Stream API chain with loop" "true"

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class Main {
  private static List<Integer> test(int[] numbers) {
      List<Integer> list = new ArrayList<>();
      for (int number : numbers) {
          Integer integer = number;
          list.add(integer);
      }
      return list;
  }

  public static void main(String[] args) {
    System.out.println(test(new int[] {1,2,3}));
  }
}