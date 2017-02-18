// "Replace Stream API chain with loop" "true"

import java.util.Arrays;
import java.util.List;

public class Main {
  private static Integer test(List<Integer> numbers) {
    return numbers.stream().red<caret>uce(0, Math::max);
  }

  public static void main(String[] args) {
    test(Arrays.asList("a", "b", "xyz"));
  }
}