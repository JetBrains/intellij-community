// "Replace Stream API chain with loop" "true"

import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class Main {
  private static TreeSet<Integer> test() {
      TreeSet<Integer> collection = new TreeSet<Integer>();
      for (int i : new int[]{4, 2, 1}) {
          Integer integer = i;
          collection.add(integer);
      }
      return collection;
  }

  public static void main(String[] args) {
    System.out.println(test());
  }
}