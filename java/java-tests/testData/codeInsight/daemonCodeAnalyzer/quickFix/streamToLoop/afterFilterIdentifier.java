// "Replace Stream API chain with loop" "true"

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class Main {
  private static test(List<String> packages) {
      Optional<String> found = Optional.empty();
      for (String s : packages) {
          if (s.startsWith("xyz")) {
              found = Optional.of(s);
              break;
          }
      }
      return found.filter(pkg -> pkg.endsWith("abc")).isPresent();
  }

  public static void main(String[] args) {
    System.out.println(test(Arrays.asList("xyzabc", "xyz123", "123abc", "123")));
  }
}