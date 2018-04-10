// "Replace with findFirst()" "true"

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class Main {
  private static String test(List<String> list) {
    Optional<String> found = Optional.empty();
    for (String s : li<caret>st) {
      if (Objects.nonNull(s)) {
        found = Optional.of(s); // optional!
        break;
      }
    }
    return found.orElse(null);
  }

  public static void main(String[] args) {
    System.out.println(test(Arrays.asList(null, null, "aa", "bbb", "c", null, "dd")));
  }
}