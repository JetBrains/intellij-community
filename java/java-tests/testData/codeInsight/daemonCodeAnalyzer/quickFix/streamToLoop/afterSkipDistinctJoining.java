// "Fix all 'Stream API call chain can be replaced with loop' problems in file" "true"

import java.util.*;
import java.util.stream.Collectors;

public class Main {
  private static String test0(List<CharSequence> list) {
      StringBuilder sb = new StringBuilder();
      Set<CharSequence> uniqueValues = new HashSet<>();
      boolean first = true;
      for (CharSequence charSequence : list) {
          if (first) {
              first = false;
              continue;
          }
          if (uniqueValues.add(charSequence)) {
              sb.append(charSequence);
          }
      }
      return sb.toString();
  }

  private static String test1(List<CharSequence> list, String delimiter) {
      StringJoiner joiner = new StringJoiner(delimiter);
      long toSkip = 2;
      Set<CharSequence> uniqueValues = new HashSet<>();
      boolean first = true;
      for (CharSequence charSequence : list) {
          if (first) {
              first = false;
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

  private static String test3(List<CharSequence> list, String delimiter) {
      StringJoiner joiner = new StringJoiner(delimiter, "<", ">");
      Set<CharSequence> uniqueValues = new HashSet<>();
      boolean first = true;
      for (CharSequence charSequence : list) {
          if (first) {
              first = false;
              continue;
          }
          if (uniqueValues.add(charSequence)) {
              joiner.add(charSequence);
          }
      }
      return joiner.toString();
  }

  public static void main(String[] args) {
    System.out.println(test0(Arrays.asList("a", "b", "e", "c", "d", "e", "a")));
    System.out.println(test1(Arrays.asList("a", "b", "e", "c", "d", "e", "a"), ";"));
    System.out.println(test3(Arrays.asList("a", "b", "e", "c", "d", "e", "a"), ";"));
  }
}