// "Replace Stream API chain with loop" "true"

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class Main {
  private static test(List<String> packages) {
      for (String s : packages) {
          if (s.startsWith("xyz")) {
              return Optional.of(s).filter(pkg -> pkg.endsWith("abc")).isPresent();
          }
      }
      return Optional.<String>empty().filter(pkg -> pkg.endsWith("abc")).isPresent();
  }

  public static void main(String[] args) {
    System.out.println(test(Arrays.asList("xyzabc", "xyz123", "123abc", "123")));
  }
}