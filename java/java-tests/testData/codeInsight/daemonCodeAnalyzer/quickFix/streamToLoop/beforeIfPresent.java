// "Replace Stream API chain with loop" "true"

import java.util.Arrays;
import java.util.List;

public class Main {
  private static void test(List<String> list) {
    list.stream().filter(str -> str.contains("x")).fin<caret>dFirst().ifPresent(System.out::println);
  }

  public static void main(String[] args) {
    test(Arrays.asList("a", "b", "syz"));
  }
}