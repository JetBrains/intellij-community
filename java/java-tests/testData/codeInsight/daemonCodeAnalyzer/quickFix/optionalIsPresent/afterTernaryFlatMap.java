// "Replace Optional presence condition with functional style expression" "true-preview"

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class Main {
  public static Optional<String> trimmed(String s) {
    return s.isEmpty() ? Optional.empty() : Optional.of(s.trim());
  }

  private static Optional<String> test(List<Object> list) {
    Optional<Object> first = list.stream().filter(obj -> obj instanceof String).findFirst();
    return first.flatMap(o -> trimmed((String) o));
  }

  public static void main(String[] args) {
    System.out.println(test(Arrays.asList(null, null, "aa", "bbb", "c", null, "dd")));
  }
}