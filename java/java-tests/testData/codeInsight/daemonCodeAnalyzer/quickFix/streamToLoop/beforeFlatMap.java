// "Fix all 'Stream API call chain can be replaced with loop' problems in file" "true"

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.Arrays.asList;

public class Main {
  private static long testChain(List<? extends String> list) {
    return Stream.of(0, null, "1", list).flatMap(Stream::of).flatMap(Stream::of).flatMap(Stream::of).flatMap(Stream::of).flatMap(Stream::of).cou<caret>nt();
  }

  public static void testComplexFilter(List<String> list) {
    System.out.println(list.stream()
                         .filter(x -> x != null)
                         .flatMap(s -> IntStream.range(0, 10).boxed().filter(Predicate.isEqual(s.length())))
                         .collect(Collectors.toList()));
  }

  public void testConditional(List<List<String>> list) {
    list.stream().flatMap(lst -> lst == null ? Stream.empty() : lst.stream()).forEach(System.out::println);
  }

  private static long testDistinctUnpluralize(List<List<String>> nested) {
    return nested.stream().flatMap(names -> names.stream().distinct()).count();
  }

  public static IntSummaryStatistics testLimit() {
    return IntStream.range(0, 100).flatMap(x -> IntStream.range(0, x).limit(x/2)).limit(50).summaryStatistics();
  }

  public static IntSummaryStatistics testLimit3() {
    return IntStream.range(0, 100).flatMap(x -> IntStream.range(0, x).flatMap(y -> IntStream.range(y, y + 100).limit(10)).limit(x / 2)).limit(500).summaryStatistics();
  }

  public static IntSummaryStatistics testLimitCrazy() {
    return IntStream.range(0, 100).flatMap(
      x -> IntStream.range(0, x).flatMap(
        y -> IntStream.range(y, y + 100).flatMap(
          z -> IntStream.range(z, z+2)).limit(10))
        .limit(x / 2))
      .limit(500)
      .summaryStatistics();
  }

  private static List<String> testMethodRef(List<List<String>> list) {
    return list.stream().flatMap(Collection::stream).collect(Collectors.toList());
  }

  private static List<String> testMethodRef2(List<String[]> list) {
    return list.stream().flatMap(Stream::of).collect(Collectors.toList());
  }

  private static List<List<String>> testMethodRef3(List<List<List<String>>> list) {
    return list.stream().flatMap(List::stream).collect(Collectors.toList());
  }

  private static long testBoundRename(Map<String, List<String>> strings) {
    return strings.entrySet().stream().filter(e -> !e.getKey().isEmpty())
      .flatMap(entry -> entry.getValue().stream().filter(entry.getKey()::equals))
      .count();
  }

  public static IntSummaryStatistics testNestedFlatMap(List<List<List<String>>> list) {
    return list.stream().filter(l -> l != null).flatMap(l -> l.stream().filter(lst -> lst != null).flatMap(lst -> lst.stream())).mapToInt(str -> str.length()).summaryStatistics();
  }

  public static LongSummaryStatistics testNestedMap(List<List<String>> list) {
    return list.stream().filter(a -> a != null).flatMapToLong(lst -> lst.stream().mapToLong(a -> a.length())).summaryStatistics();
  }

  public static IntSummaryStatistics testNestedSkip(int... values) {
    return Arrays.stream(values).skip(2).filter(x -> x > 0).flatMap(v -> IntStream.range(0, 100).skip(v)).summaryStatistics();
  }

  public static IntSummaryStatistics testNestedSkip2(int... values) {
    return Arrays.stream(values).filter(x -> x > 0).flatMap(v -> IntStream.range(0, 100).skip(v)).skip(2).summaryStatistics();
  }

  public String testSorted(List<List<String>> list) {
    return list.stream().flatMap(lst -> lst.stream().filter(Objects::nonNull).sorted()).filter(x -> x.length() < 5).findFirst().orElse("");
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