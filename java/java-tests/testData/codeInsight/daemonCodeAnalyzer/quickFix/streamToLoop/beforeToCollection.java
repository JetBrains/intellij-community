// "Replace Stream API chain with loop" "true"

import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class Main {
  private static TreeSet<Integer> test() {
    return IntStream.of(4, 2, 1).boxed().col<caret>lect(Collectors.toCollection(TreeSet::new));
  }

  public static void main(String[] args) {
    System.out.println(test());
  }
}