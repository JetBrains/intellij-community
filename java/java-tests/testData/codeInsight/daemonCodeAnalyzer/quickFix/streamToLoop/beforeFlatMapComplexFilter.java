// "Replace Stream API chain with loop" "true"

import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class Main {
  public static void test(List<String> list) {
    System.out.println(list.stream()
                         .filter(x -> x != null)
                         .flatMap(s -> IntStream.range(0, 10).boxed().filter(Predicate.isEqual(s.length())))
                         .co<caret>llect(Collectors.toList()));
  }

  public static void main(String[] args) {
    test(Arrays.asList("a", "bbbb", "cccccccccc", "dd", ""));
  }
}
