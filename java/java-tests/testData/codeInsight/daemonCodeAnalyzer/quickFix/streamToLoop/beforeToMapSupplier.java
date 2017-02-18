// "Replace Stream API chain with loop" "true"

import java.util.*;
import java.util.stream.Collectors;

public class Main {
  public static TreeMap<Integer, String> test(List<String> strings) {
    return strings.stream().filter(s -> !s.isEmpty())
      .col<caret>lect(Collectors.toMap(String::length, s -> s, (s, string) -> s, TreeMap::new));
  }

  public static void main(String[] args) {
    System.out.println(test(Arrays.asList()));
    System.out.println(test(Arrays.asList("a", "bbb", "cc", "d", "eee", "")));
  }
}