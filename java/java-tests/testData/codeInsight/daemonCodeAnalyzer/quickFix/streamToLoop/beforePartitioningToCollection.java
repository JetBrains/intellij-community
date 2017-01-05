// "Replace Stream API chain with loop" "true"

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

public class Main {
  public static void test(List<String> strings) {
    System.out.println(strings.stream().co<caret>llect(Collectors.partitioningBy(s -> s.length() > 2, Collectors.toCollection(LinkedHashSet::new))));
  }

  public static void main(String[] args) {
    test(Arrays.asList("a", "bbb", "cccc", "dddd", "ee", "e", "e"));
  }
}
