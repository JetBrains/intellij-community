// "Replace Stream API chain with loop" "true"

import java.util.*;
import java.util.stream.Collectors;

public class Main {
  public static Map<Integer, List<String>> test(List<String> strings) {
    return strings.stream().col<caret>lect(Collectors.groupingBy(str -> str.length()));
  }

  public static void main(String[] args) {
    System.out.println(test(Arrays.asList()));
    System.out.println(test(Arrays.asList("a", "bbb", "cc", "d", "eee")));
  }
}