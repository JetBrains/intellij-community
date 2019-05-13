// "Fix all 'Stream API call chain can be replaced with loop' problems in file" "true"

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.Arrays.asList;

public class Main {
  private static long testChain(List<? extends String> list) {
      long count = 0L;
      for (Object o : Arrays.asList(0, null, "1", list)) {
          for (Object o1 : Arrays.asList(o)) {
              for (Object o2 : Arrays.asList(o1)) {
                  for (Object o3 : Arrays.asList(o2)) {
                      for (Object o4 : Arrays.asList(o3)) {
                          for (Object o5 : Arrays.asList(o4)) {
                              count++;
                          }
                      }
                  }
              }
          }
      }
      return count;
  }

  public static void testComplexFilter(List<String> list) {
      List<Integer> result = new ArrayList<>();
      for (String x : list) {
          if (x != null) {
              Predicate<Integer> predicate = Predicate.isEqual(x.length());
              for (int i = 0; i < 10; i++) {
                  Integer integer = i;
                  if (predicate.test(integer)) {
                      result.add(integer);
                  }
              }
          }
      }
      System.out.println(result);
  }

  public void testConditional(List<List<String>> list) {
      for (List<String> lst : list) {
          if (lst != null) {
              for (String s : lst) {
                  System.out.println(s);
              }
          }
      }
  }

  private static long testDistinctUnpluralize(List<List<String>> nested) {
      long count = 0L;
      for (List<String> names : nested) {
          Set<String> uniqueValues = new HashSet<>();
          for (String name : names) {
              if (uniqueValues.add(name)) {
                  count++;
              }
          }
      }
      return count;
  }

  public static IntSummaryStatistics testLimit() {
      IntSummaryStatistics stat = new IntSummaryStatistics();
      long limit = 50;
      OUTER:
      for (int x = 0; x < 100; x++) {
          long limitInner = x / 2;
          for (int i = 0; i < x; i++) {
              if (limitInner-- == 0) break;
              if (limit-- == 0) break OUTER;
              stat.accept(i);
          }
      }
      return stat;
  }

  public static IntSummaryStatistics testLimit3() {
      IntSummaryStatistics stat = new IntSummaryStatistics();
      long limit = 500;
      OUTER:
      for (int x = 0; x < 100; x++) {
          long limitInner = x / 2;
          INNER:
          for (int y = 0; y < x; y++) {
              long limit1 = 10;
              int bound = y + 100;
              for (int i = y; i < bound; i++) {
                  if (limit1-- == 0) break;
                  if (limitInner-- == 0) break INNER;
                  if (limit-- == 0) break OUTER;
                  stat.accept(i);
              }
          }
      }
      return stat;
  }

  public static IntSummaryStatistics testLimitCrazy() {
      IntSummaryStatistics stat = new IntSummaryStatistics();
      long limit = 500;
      OUTER:
      for (int x = 0; x < 100; x++) {
          long limitInner = x / 2;
          INNER:
          for (int y = 0; y < x; y++) {
              long limit1 = 10;
              int boundInner = y + 100;
              INNER1:
              for (int z = y; z < boundInner; z++) {
                  int bound = z + 2;
                  for (int i = z; i < bound; i++) {
                      if (limit1-- == 0) break INNER1;
                      if (limitInner-- == 0) break INNER;
                      if (limit-- == 0) break OUTER;
                      stat.accept(i);
                  }
              }
          }
      }
      return stat;
  }

  private static List<String> testMethodRef(List<List<String>> list) {
      List<String> result = new ArrayList<>();
      for (List<String> strings : list) {
          for (String string : strings) {
              result.add(string);
          }
      }
      return result;
  }

  private static List<String> testMethodRef2(List<String[]> list) {
      List<String> result = new ArrayList<>();
      for (String[] strings : list) {
          for (String t : strings) {
              result.add(t);
          }
      }
      return result;
  }

  private static List<List<String>> testMethodRef3(List<List<List<String>>> list) {
      List<List<String>> result = new ArrayList<>();
      for (List<List<String>> lists : list) {
          for (List<String> strings : lists) {
              result.add(strings);
          }
      }
      return result;
  }

  private static long testBoundRename(Map<String, List<String>> strings) {
      long count = 0L;
      for (Map.Entry<String, List<String>> e : strings.entrySet()) {
          if (!e.getKey().isEmpty()) {
              String sInner = e.getKey();
              for (String s : e.getValue()) {
                  if (sInner.equals(s)) {
                      count++;
                  }
              }
          }
      }
      return count;
  }

  public static IntSummaryStatistics testNestedFlatMap(List<List<List<String>>> list) {
      IntSummaryStatistics stat = new IntSummaryStatistics();
      for (List<List<String>> l : list) {
          if (l != null) {
              for (List<String> lst : l) {
                  if (lst != null) {
                      for (String str : lst) {
                          int length = str.length();
                          stat.accept(length);
                      }
                  }
              }
          }
      }
      return stat;
  }

  public static LongSummaryStatistics testNestedMap(List<List<String>> list) {
      LongSummaryStatistics stat = new LongSummaryStatistics();
      for (List<String> a : list) {
          if (a != null) {
              for (String s : a) {
                  long length = s.length();
                  stat.accept(length);
              }
          }
      }
      return stat;
  }

  public static IntSummaryStatistics testNestedSkip(int... values) {
      IntSummaryStatistics stat = new IntSummaryStatistics();
      long toSkipOuter = 2;
      for (int x : values) {
          if (toSkipOuter > 0) {
              toSkipOuter--;
              continue;
          }
          if (x > 0) {
              long toSkip = x;
              for (int i = 0; i < 100; i++) {
                  if (toSkip > 0) {
                      toSkip--;
                      continue;
                  }
                  stat.accept(i);
              }
          }
      }
      return stat;
  }

  public static IntSummaryStatistics testNestedSkip2(int... values) {
      IntSummaryStatistics stat = new IntSummaryStatistics();
      long toSkip = 2;
      for (int x : values) {
          if (x > 0) {
              long toSkipInner = x;
              for (int i = 0; i < 100; i++) {
                  if (toSkipInner > 0) {
                      toSkipInner--;
                      continue;
                  }
                  if (toSkip > 0) {
                      toSkip--;
                      continue;
                  }
                  stat.accept(i);
              }
          }
      }
      return stat;
  }

  public String testSorted(List<List<String>> list) {
      for (List<String> lst : list) {
          List<String> toSort = new ArrayList<>();
          for (String x : lst) {
              if (x != null) {
                  toSort.add(x);
              }
          }
          toSort.sort(null);
          for (String x : toSort) {
              if (x.length() < 5) {
                  return x;
              }
          }
      }
      return "";
  }

  public static void main(String[] args) {
    testChain(asList("aa", "bbb", "c", null, "dd"));
    testComplexFilter(asList("a", "bbbb", "cccccccccc", "dd", ""));
    System.out.println(testDistinctUnpluralize(asList(asList("a"), asList(null, "bb", "ccc"))));
    System.out.println(testLimit());
    System.out.println(testLimit3());
    System.out.println(testLimitCrazy());
    System.out.println(testMethodRef(asList(asList("", "a", "abcd", "xyz"), asList("x", "y"))));
    System.out.println(testMethodRef2(asList(new String[] {"", "a", "abcd", "xyz"}, new String[] {"x", "y"})));
    System.out.println(testMethodRef3(asList(asList(asList("a", "d")), asList(asList("c"), asList("b")))));
    System.out.println(testNestedFlatMap(asList(asList(asList("a", "bbb", "ccc")), asList(), null, asList(asList("z")))));
    System.out.println(testNestedMap(asList(null, asList("aaa", "b", "cc", "dddd"), asList("gggg"))));
    System.out.println(testNestedSkip(1, 95, -2, 0, 97, 90));
    System.out.println(testNestedSkip2(1, 95, -2, 0, 97, 90));

    Map<String, List<String>> map = new HashMap<>();
    map.put("", asList("", "a", "b"));
    map.put("a", asList("", "a", "b", "a"));
    map.put("b", asList("", "a", "b"));
    map.put("c", asList("", "a", "b"));
    System.out.println(testBoundRename(map));
  }
}