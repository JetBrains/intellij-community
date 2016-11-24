// "Replace Stream API chain with loop" "true"

import java.util.*;
import java.util.stream.Collectors;

public class Main {
  public static Optional<String> test(List<String> strings) {
    return strings.stream().filter(s -> !s.isEmpty()).c<caret>ollect(Collectors.maxBy(Comparator.naturalOrder()));
  }

  public static void main(String[] args) {
    System.out.println(test(Arrays.asList()));
    System.out.println(test(Arrays.asList("a", "bbb", "cc", "d", "eee", "")));
  }
}