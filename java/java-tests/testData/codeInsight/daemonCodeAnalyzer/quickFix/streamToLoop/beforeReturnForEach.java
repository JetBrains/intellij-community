// "Replace Stream API chain with loop" "false"

import java.util.Arrays;
import java.util.List;

public class Main {
  private static void test(List<String> list) {
    return list.stream().filter(x -> x != null).forEac<caret>h(y -> System.out.println(y));
  }

  public static void main(String[] args) {
    test(Arrays.asList("a", "b", "xyz"));
  }
}