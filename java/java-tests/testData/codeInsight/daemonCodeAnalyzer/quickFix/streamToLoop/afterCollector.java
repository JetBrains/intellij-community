// "Fix all 'Stream API call chain can be replaced with loop' problems in file" "true"

import static java.util.Arrays.asList;
import java.util.*;
import java.util.stream.*;

public class Main {
  public void testAveragingDouble(String... list) {
      double sum = 0;
      long count = 0;
      for (String s : list) {
          if (s != null) {
              sum += 1.0 / s.length();
              count++;
          }
      }
      System.out.println(count > 0 ? sum / count : 0.0);
  }

  public void testAveragingInt(String... list) {
      long sum = 0;
      long count = 0;
      for (String s : list) {
          if (s != null) {
              sum += s.length();
              count++;
          }
      }
      System.out.println(count > 0 ? (double) sum / count : 0.0);
  }

  public static long testCounting(List<String> strings) {
      long count = 0L;
      for (String s : strings) {
          if (!s.isEmpty()) {
              count++;
          }
      }
      return count;
  }

  public static Optional<String> testMaxBy(List<String> strings) {
      boolean seen = false;
      String best = null;
      for (String s : strings) {
          if (!s.isEmpty()) {
              if (!seen || s.compareTo(best) > 0) {
                  seen = true;
                  best = s;
              }
          }
      }
      return seen ? Optional.of(best) : Optional.empty();
  }

  public static Optional<String> testReducing1(List<String> list) {
      boolean seen = false;
      String acc = null;
      for (String s : list) {
          if (!seen) {
              seen = true;
              acc = s;
          } else {
              acc = acc + s;
          }
      }
      return seen ? Optional.of(acc) : Optional.empty();
  }

  public static String testReducing2(List<String> list) {
      String acc = "";
      for (String s : list) {
          acc = acc + s;
      }
      return acc;
  }

  static Integer testReducing3() {
      Integer totalLength = 0;
      for (String s : Arrays.asList("a", "bb", "ccc")) {
          Integer length = s.length();
          totalLength = totalLength + length;
      }
      return totalLength;
  }

  public static DoubleSummaryStatistics testSummarizingDouble(List<String> strings) {
      DoubleSummaryStatistics stat = new DoubleSummaryStatistics();
      for (String str : strings) {
          if (str != null) {
              stat.accept(str.length() / 2.0);
          }
      }
      return stat;
  }

  public static Double testSummingDouble(List<String> strings) {
      double sum = 0.0;
      for (String string : strings) {
          if (string != null) {
              sum += string.length();
          }
      }
      return sum;
  }

  private static TreeSet<Integer> testToCollection() {
      TreeSet<Integer> integers = new TreeSet<>();
      for (int i : new int[]{4, 2, 1}) {
          Integer integer = i;
          integers.add(integer);
      }
      return integers;
  }

  // Unresolved reference
  void f(Collection<? extends Foo> c) {
      R treeSet = new TreeSet();
      for (Foo foo : c) {
          treeSet.add(foo);
      }
      Set<Foo> uniqueDescriptors = treeSet;
  }

  public static void main(String[] args) {
    new Main().testAveragingDouble("a", "bbb", null, "cc", "dd", "eedasfasdfs");
    new Main().testAveragingInt("a", "bbb", null, "cc", "dd", "eedasfasdfs");
    System.out.println(testCounting(Arrays.asList()));
    System.out.println(testCounting(Arrays.asList("a", "bbb", "cc", "d", "eee", "")));
    System.out.println(testMaxBy(Arrays.asList()));
    System.out.println(testMaxBy(Arrays.asList("a", "bbb", "cc", "d", "eee", "")));
    System.out.println(testReducing1(asList("a", "b", "c")));
    System.out.println(testReducing2(asList("a", "b", "c")));
    System.out.println(testReducing3());
    System.out.println(testSummarizingDouble(Arrays.asList(null, null)));
    System.out.println(testSummarizingDouble(Arrays.asList("a", "bbb", "cc", "d", "eee", "")));
    System.out.println(testSummingDouble(Arrays.asList(null, null)));
    System.out.println(testSummingDouble(Arrays.asList("a", "bbb", "cc", "d", "eee", "")));
    System.out.println(testToCollection());
  }
}
