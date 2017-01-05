// "Replace Stream API chain with loop" "true"

import java.util.*;
import java.util.stream.Collectors;

public class Main {
  private static String test(List<CharSequence> list, String delimiter) {
      StringJoiner joiner = new StringJoiner(delimiter);
      long toSkip = 2;
      Set<CharSequence> uniqueValues = new HashSet<>();
      long toSkip1 = 1;
      for (CharSequence charSequence : list) {
          if (toSkip1 > 0) {
              toSkip1--;
              continue;
          }
          if (uniqueValues.add(charSequence)) {
              if (toSkip > 0) {
                  toSkip--;
                  continue;
              }
              joiner.add(charSequence);
          }
      }
      return joiner.toString();
  }

  public static void main(String[] args) {
    System.out.println(test(Arrays.asList("a", "b", "e", "c", "d", "e", "a"), ";"));
  }
}