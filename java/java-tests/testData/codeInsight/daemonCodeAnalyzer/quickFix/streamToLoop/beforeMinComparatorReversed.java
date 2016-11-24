// "Replace Stream API chain with loop" "true"

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class Main {
  public static String test(List<String> strings, Comparator<CharSequence> comparator) {
    return strings.stream().m<caret>in(comparator.reversed()).orElse(null);
  }

  public static void main(String[] args) {
    System.out.println(test(Arrays.asList(), Comparator.comparing(CharSequence::length)));
    System.out.println(test(Arrays.asList("a", "bbb", "cc", "d", "eee"), Comparator.comparing(CharSequence::length)));
  }
}