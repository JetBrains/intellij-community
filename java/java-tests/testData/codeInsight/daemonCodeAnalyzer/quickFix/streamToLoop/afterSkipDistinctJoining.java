// "Replace Stream API chain with loop" "true"

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class Main {
  private static String test(List<CharSequence> list) {
      StringBuilder sb = new StringBuilder();
      Set<CharSequence> uniqueValues = new HashSet<>();
      long toSkip = 1;
      for (CharSequence charSequence : list) {
          if (toSkip > 0) {
              toSkip--;
              continue;
          }
          if (uniqueValues.add(charSequence)) {
              sb.append(charSequence);
          }
      }
      return sb.toString();
  }

  public static void main(String[] args) {
    System.out.println(test(Arrays.asList("a", "b", "e", "c", "d", "e", "a")));
  }
}