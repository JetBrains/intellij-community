// "Replace Stream API chain with loop" "true"

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class Main {
  private static String test(List<String> list) {
    if (list == null) return null;
    else {
        Optional<String> found = Optional.empty();
        for (String str : list) {
            if (str.contains("x")) {
                found = Optional.of(str);
                break;
            }
        }
        return found.orElse(null);
    }
  }

  public static void main(String[] args) {
    System.out.println(test(Arrays.asList("a", "b", "syz")));
  }
}