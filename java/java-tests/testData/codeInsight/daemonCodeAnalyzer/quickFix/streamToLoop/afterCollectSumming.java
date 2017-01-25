// "Replace Stream API chain with loop" "true"

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class Main {
  public static Double test(List<String> strings) {
      double sum = 0.0;
      for (String string : strings) {
          if (string != null) {
              sum += string.length();
          }
      }
      return sum;
  }

  public static void main(String[] args) {
    System.out.println(test(Arrays.asList(null, null)));
    System.out.println(test(Arrays.asList("a", "bbb", "cc", "d", "eee", "")));
  }
}