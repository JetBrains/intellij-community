import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.*;
import java.util.stream.*;

public class StreamCollectorInlining {

  void testCounting() {
    long count = <warning descr="Result of 'Stream.empty().collect(Collectors.counting())' is always '0'">Stream.empty().collect(Collectors.counting())</warning>;
    if(<warning descr="Condition 'count > 0' is always 'false'">count > 0</warning>) {
      System.out.println("impossible");
    }
  }

  void testToListNoSideEffect(List<String> list) {
    if(list.isEmpty()) return;
    List<String> other = Stream.of("foo", "bar", "baz").collect(Collectors.toList());
    // We can conclude now that this stream produces no side effect, thus list.isEmpty() result is still valid
    if(<warning descr="Condition 'list.isEmpty()' is always 'false'">list.isEmpty()</warning>) return;
  }

  void testToCollection(List<String> list) {
    list.stream().collect(Collectors.toCollection(() -> <warning descr="Function may return null, but it's not allowed here">null</warning>));
  }

  @Nullable
  final String convert(String s) {
    return s.isEmpty() ? null : s;
  }

  Map<String, String> testToMapNullableValue(List<String> list) {
    return list.stream().collect(Collectors.toMap(
      this::convert, <warning descr="Function may return null, but it's not allowed here">this::convert</warning>));
  }

  Map<String, String> testToMapNullableValueMerger(List<String> list) {
    return list.stream().collect(
      Collectors.toMap(this::convert, <warning descr="Function may return null, but it's not allowed here">this::convert</warning>,
                 (a, b) -> <warning descr="Condition 'a == null' is always 'false'">a == null</warning> ? b : a));
  }

  Map<String, String> testToMapNullableValueMerger2(List<String> list) {
    return list.stream().collect(
      Collectors.toMap(this::convert, <warning descr="Function may return null, but it's not allowed here">this::convert</warning>,
                (a, b) -> <warning descr="Condition 'b == null' is always 'false'">b == null</warning> ? b : a));
  }

  Map<String, String> testToMapNullableValueSupplier(List<String> list) {
    return list.stream().collect(
      Collectors.toMap(this::convert, <warning descr="Function may return null, but it's not allowed here">this::convert</warning>,
                (a, b) -> b, () -> <warning descr="Function may return null, but it's not allowed here">null</warning>));
  }

  void testListLocality(List<String> list) {
    List<String> result = list.stream().filter(Objects::nonNull).collect(Collectors.toList());
    if(result.isEmpty()) return;
    foo();
    if(<warning descr="Condition 'result.isEmpty()' is always 'false'">result.isEmpty()</warning>) return;
    foo();
  }

  native void foo();

  void testNotNull(List<String> list) {
    String res = list.stream().collect(Collectors.joining());
    if (<warning descr="Condition 'res == null' is always 'false'">res == null</warning>) return;
    Double x = list.stream().collect(Collectors.averagingInt(String::length));
    if (<warning descr="Condition 'x == null' is always 'false'">x == null</warning>) return;
    Integer sum = list.stream().collect(Collectors.summingInt(String::length));
    if (<warning descr="Condition 'sum == null' is always 'false'">sum == null</warning>) return;
    IntSummaryStatistics stat = list.stream().collect(Collectors.summarizingInt(String::length));
    if (<warning descr="Condition 'stat == null' is always 'false'">stat == null</warning>) return;
  }

}
