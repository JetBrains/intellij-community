// "Replace Stream API chain with loop" "true-preview"

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class Main {
  private static List<Integer> test(int[] numbers) {
    return Arrays.stream(numbers).boxed().c<caret>ollect(Collectors.toList());
  }

  public static void main(String[] args) {
    System.out.println(test(new int[] {1,2,3}));
  }
}