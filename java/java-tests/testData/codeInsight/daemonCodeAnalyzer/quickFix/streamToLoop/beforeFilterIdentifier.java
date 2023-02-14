// "Replace Stream API chain with loop" "true-preview"

import java.util.Arrays;
import java.util.List;

public class Main {
  private static test(List<String> packages) {
    return packages.stream().filter(pkg -> pkg.startsWith("xyz")).fin<caret>dAny().filter(pkg -> pkg.endsWith("abc")).isPresent();
  }

  public static void main(String[] args) {
    System.out.println(test(Arrays.asList("xyzabc", "xyz123", "123abc", "123")));
  }
}