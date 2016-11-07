// "Replace Stream API chain with loop" "true"

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class Main {
  private static List<Integer> getPositiveDoubled(int... input) {
    return Arrays.stream(input).filter(x -> x > 0).mapToObj(x -> x*2).c<caret>ollect(Collectors.toList());
  }

  public static void main(String[] args) {
    System.out.println(getPositiveDoubled(1, 2, 3, -1, -2));
  }
}