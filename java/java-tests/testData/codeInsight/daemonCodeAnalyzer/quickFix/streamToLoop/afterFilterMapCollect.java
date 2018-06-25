// "Replace Stream API chain with loop" "true"

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class Main {
  private static List<Integer> getPositiveDoubled(int... input) {
      List<Integer> list = new ArrayList<>();
      for (int x : input) {
          if (x > 0) {
              Integer integer = x * 2;
              list.add(integer);
          }
      }
      return list;
  }

  public static void main(String[] args) {
    System.out.println(getPositiveDoubled(1, 2, 3, -1, -2));
  }
}