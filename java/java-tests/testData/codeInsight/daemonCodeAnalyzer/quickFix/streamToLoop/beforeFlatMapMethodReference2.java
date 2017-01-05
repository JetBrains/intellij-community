// "Replace Stream API chain with loop" "true"

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {
  private static List<String> test(List<String[]> list) {
    return list.stream().flatMap(Stream::of).col<caret>lect(Collectors.toList());
  }

  public static void main(String[] args) {
    System.out.println(test(Arrays.asList(new String[] {"", "a", "abcd", "xyz"}, new String[] {"x", "y"})));
  }
}