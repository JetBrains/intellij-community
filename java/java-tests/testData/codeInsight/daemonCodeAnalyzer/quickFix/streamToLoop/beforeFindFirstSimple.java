// "Replace Stream API chain with loop" "true"

import java.util.List;
import java.util.Optional;

import static java.util.Arrays.asList;

public class Main {
  public static Optional<String> test(List<String> list) {
    return list.stream().fi<caret>ndFirst();
  }

  public static void main(String[] args) {
    System.out.println(test(asList("a", "b", "c")));
  }
}