// "Replace Stream API chain with loop" "true"

import java.util.*;
import java.util.stream.Collectors;

public class Main {
  public static Map<Integer, String> test(List<String> strings) {
    Map<Integer, String> mapping = strings.stream()
      .co<caret>llect(Collectors.toMap(String::length, str -> str));
    return mapping;
  }

  public static void main(String[] args) {
    System.out.println(test(Arrays.asList()));
    System.out.println(test(Arrays.asList("a", "bbb", "cc", "d", "eee", "")));
  }
}