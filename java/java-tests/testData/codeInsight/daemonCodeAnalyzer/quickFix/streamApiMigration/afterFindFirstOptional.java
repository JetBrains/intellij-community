// "Replace with findFirst()" "true"

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class Main {
  private static String test(List<String> list) {
      Optional<String> found = list.stream().filter(Objects::nonNull).findFirst();
      return found.orElse(null);
  }

  public static void main(String[] args) {
    System.out.println(test(Arrays.asList(null, null, "aa", "bbb", "c", null, "dd")));
  }
}