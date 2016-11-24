// "Replace Stream API chain with loop" "true"

import java.util.Arrays;
import java.util.List;

public class Main {
  public static int test(List<String> strings) {
    return strings.stream().mapToInt(String::length).mi<caret>n().orElse(-1);
  }

  public static void main(String[] args) {
    System.out.println(test(Arrays.asList()));
    System.out.println(test(Arrays.asList("a", "bbb", "cc", "d")));
  }
}