// "Replace Stream API chain with loop" "true"

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class Main {
  private static List<?>[] test(int[] numbers) {
    return Arrays.stream(numbers).boxed().map(n -> Collections.singletonList(n)).toA<caret>rray(List<?>[]::new);
  }

  public static void main(String[] args) {
    System.out.println(Arrays.asList(test(new int[] {1,2,3})));
  }
}