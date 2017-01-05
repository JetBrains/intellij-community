// "Replace Stream API chain with loop" "true"

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class Main {
  private static List<String> test(List<List<String>> list) {
    return list.stream().flatMap(Collection::stream).col<caret>lect(Collectors.toList());
  }

  public static void main(String[] args) {
    System.out.println(test(Arrays.asList(Arrays.asList("", "a", "abcd", "xyz"), Arrays.asList("x", "y"))));
  }
}