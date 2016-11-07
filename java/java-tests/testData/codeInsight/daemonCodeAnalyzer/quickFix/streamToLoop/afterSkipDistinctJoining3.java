// "Replace Stream API chain with loop" "true"

import java.util.*;
import java.util.stream.Collectors;

public class Main {
  private static String test(List<CharSequence> list, String delimiter) {
      StringJoiner joiner = new StringJoiner(delimiter, "<", ">");
      Set<CharSequence> uniqueValues = new HashSet<>();
      long toSkip = 1;
      for (CharSequence charSequence : list) {
          if (toSkip > 0) {
              toSkip--;
              continue;
          }
          if (uniqueValues.add(charSequence)) {
              joiner.add(charSequence);
          }
      }
      return joiner.toString();
  }

  public static void main(String[] args) {
    System.out.println(test(Arrays.asList("a", "b", "e", "c", "d", "e", "a"), ";"));
  }
}