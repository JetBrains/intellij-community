// "Replace Stream API chain with loop" "true-preview"

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class Main {
  private static List<Integer> test(int[] numbers) {
      List<Integer> list = new ArrayList<>();
      for (int number : numbers) {
          Integer i = number;
          list.add(i);
      }
      return list;
  }

  public static void main(String[] args) {
    System.out.println(test(new int[] {1,2,3}));
  }
}