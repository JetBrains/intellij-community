// "Fix all 'Stream API call chain can be replaced with loop' problems in file" "true"

import java.util.*;
import java.util.function.IntSupplier;

public class Main {
  public static String testMaxComparator(List<String> strings) {
      boolean seen = false;
      String best = null;
      Comparator<String> comparator = Comparator.comparing(String::length);
      for (String string: strings) {
          if (!seen || comparator.compare(string, best) > 0) {
              seen = true;
              best = string;
          }
      }
      return seen ? best : null;
  }

  public static OptionalDouble testMaxDouble(List<String> strings) {
      boolean seen = false;
      double best = 0;
      for (String string: strings) {
          double length = string.length();
          if (!seen || Double.compare(length, best) > 0) {
              seen = true;
              best = length;
          }
      }
      return seen ? OptionalDouble.of(best) : OptionalDouble.empty();
  }

  private static Optional<String> testMaxLambda(Map<String, List<String>> dependencies, String fruits, Map<String, Integer> weights) {
      boolean seen = false;
      String best = null;
      for (String s: dependencies.get(fruits)) {
          if (!seen || weights.get(s) - weights.get(best) > 0) {
              seen = true;
              best = s;
          }
      }
      return seen ? Optional.of(best) : Optional.empty();
  }

  private static Optional<String> testMaxLambdaTernary(Map<String, List<String>> dependencies, String fruits, Map<String, String> weights) {
      boolean seen = false;
      String best = null;
      for (String s: dependencies.get(fruits)) {
          if (!seen || (s.compareTo(best) < 0 ? -1 : s.compareTo(best) > 0 ? 1 : 0) > 0) {
              seen = true;
              best = s;
          }
      }
      return seen ? Optional.of(best) : Optional.empty();
  }

  private static Optional<String> testMaxReverseOrder(Map<String, List<String>> dependencies, String fruits, Map<String, String> weights) {
      boolean seen = false;
      String best = null;
      for (String s: dependencies.get(fruits)) {
          if (!seen || best.compareTo(s) > 0) {
              seen = true;
              best = s;
          }
      }
      return seen ? Optional.of(best) : Optional.empty();
  }

  public static String testMinPassedComparator(List<String> strings, Comparator<String> cmp) {
      boolean seen = false;
      String best = null;
      for (String string: strings) {
          if (!seen || cmp.compare(string, best) < 0) {
              seen = true;
              best = string;
          }
      }
      return seen ? best : null;
  }

  public static String testMinReversedComparator(List<String> strings, Comparator<CharSequence> comparator) {
      boolean seen = false;
      String best = null;
      Comparator<CharSequence> comparator1 = comparator.reversed();
      for (String string: strings) {
          if (!seen || comparator1.compare(string, best) < 0) {
              seen = true;
              best = string;
          }
      }
      return seen ? best : strings.toString();
  }

  public static int testMinInt(List<String> strings, IntSupplier supplier) {
      boolean seen = false;
      int best = 0;
      for (String string: strings) {
          int length = string.length();
          if (!seen || length < best) {
              seen = true;
              best = length;
          }
      }
      return seen ? best : supplier.getAsInt();
  }

  public static void main(String[] args) {
    System.out.println(testMaxComparator(Arrays.asList()));
    System.out.println(testMaxComparator(Arrays.asList("a", "bbb", "cc", "d", "eee")));
    System.out.println(testMaxDouble(Arrays.asList()));
    System.out.println(testMaxDouble(Arrays.asList("a", "bbb", "cc", "d")));
    System.out.println(testMinPassedComparator(Arrays.asList(), Comparator.comparing(String::length)));
    System.out.println(testMinPassedComparator(Arrays.asList("a", "bbb", "cc", "d", "eee"), Comparator.comparing(String::length)));
    System.out.println(testMinReversedComparator(Arrays.asList(), Comparator.comparing(CharSequence::length)));
    System.out.println(testMinReversedComparator(Arrays.asList("a", "bbb", "cc", "d", "eee"), Comparator.comparing(CharSequence::length)));
    System.out.println(testMinInt(Arrays.asList(), () -> -1));
    System.out.println(testMinInt(Arrays.asList("a", "bbb", "cc", "d"), () -> 2));
  }
}