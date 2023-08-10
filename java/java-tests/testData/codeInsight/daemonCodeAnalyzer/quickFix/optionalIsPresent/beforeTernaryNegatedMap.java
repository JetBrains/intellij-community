// "Replace Optional presence condition with functional style expression" "true-preview"

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class Main {
  private static Optional<String> test(List<Object> list) {
    Optional<Object> first = list.stream().filter(obj -> obj instanceof String).findFirst();
    return !fi<caret>rst.isPresent() ? Optional.empty() : Optional.of((String) first.get());
  }

  public static void main(String[] args) {
    System.out.println(test(Arrays.asList(null, null, "aa", "bbb", "c", null, "dd")));
  }
}