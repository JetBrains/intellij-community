// "Replace Stream API chain with loop" "true"

import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class Main {
  private static TreeSet<Integer> test() {
      TreeSet<Integer> integers = new TreeSet<>();
      for (int i : new int[]{4, 2, 1}) {
          Integer integer = i;
          integers.add(integer);
      }
      return integers;
  }

  public static void main(String[] args) {
    System.out.println(test());
  }
}