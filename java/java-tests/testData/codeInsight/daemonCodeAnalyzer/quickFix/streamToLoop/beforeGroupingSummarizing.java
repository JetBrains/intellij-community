// "Replace Stream API chain with loop" "true"

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class Main {
  public static void test(List<String> strings) {
    System.out.println(strings.stream()
                         .filter(Objects::nonNull)
                         .co<caret>llect(Collectors.groupingBy(String::length, Collectors.summarizingDouble(String::length))));
  }

  public static void main(String[] args) {
    test(Arrays.asList("a", "bbb", "cccc", "dddd"));
  }
}
