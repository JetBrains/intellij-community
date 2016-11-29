// "Replace Stream API chain with loop" "true"

import java.util.Arrays;
import java.util.List;

public class Main {
  private static void test(List<String> list) {
    list.stream().filter(x -> x != null).for<caret>Each(y -> System.out.println(y));
  }

  public static void main(String[] args) {
    test(Arrays.asList("a", "b", "xyz"));
  }
}