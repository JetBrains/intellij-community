// "Replace Stream API chain with loop" "true"

import java.util.List;
import java.util.Optional;

import static java.util.Arrays.asList;

public class Main {
  public static Optional<String> test(List<String> list) {
      for (String s : list) {
          return Optional.of(s);
      }
      return Optional.empty();
  }

  public static void main(String[] args) {
    System.out.println(test(asList("a", "b", "c")));
  }
}