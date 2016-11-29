// "Replace Stream API chain with loop" "true"

import java.util.Arrays;
import java.util.List;

public class Main {
  public static double test(List<String> strings) {
    return strings.stream().mapToDouble(String::length).m<caret>ax().orElse(-1);
  }

  public static void main(String[] args) {
    System.out.println(test(Arrays.asList()));
    System.out.println(test(Arrays.asList("a", "bbb", "cc", "d")));
  }
}