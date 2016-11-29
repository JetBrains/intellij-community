// "Replace Stream API chain with loop" "true"

import java.util.*;
import java.util.stream.Collectors;

public class Main {
  public static Map<Boolean, List<String>> test(List<String> strings) {
    return strings.stream().filter(s -> !s.isEmpty()).co<caret>llect(Collectors.partitioningBy(s -> s.length() > 1));
  }

  public static void main(String[] args) {
    System.out.println(test(Arrays.asList()));
    System.out.println(test(Arrays.asList("a", "bbb", "cc", "d", "eee", "")));
  }
}