// "Fix all 'Stream API call chain can be replaced with loop' problems in file" "true"

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class Main {
  private static void test(List<String> names) {
    names.stream().filter(Objects::nonNull).fo<caret>rEach(System.out::println);
  }

  private static String getString() {
    return "abc";
  }

  private static boolean testBound(List<String> strings) {
    return strings.stream().anyMatch(getString()::equals);
  }

  public static void main(String[] args) {
    test(Arrays.asList("a", "b", "xyz"));
    System.out.println(testBound(Arrays.asList("a", "b", "c")));
  }
}