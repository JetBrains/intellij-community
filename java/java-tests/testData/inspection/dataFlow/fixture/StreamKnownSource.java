import java.util.*;
import java.util.stream.*;

public class StreamKnownSource {
  void testFlatMap(List<String> list) {
    long count = list.stream().<String>flatMap(s -> Stream.empty()).count();
    if(<warning descr="Condition 'count > 0' is always 'false'">count > 0</warning>) {
      System.out.println("Impossible");
    }
  }

  void testEmptyStream(List<String> list) {
    long result = Stream.empty().count();

    if (<warning descr="Condition 'result > 0' is always 'false'">result > 0</warning>) {
      System.out.println("Never");
    }

    int sum = list.stream().filter(Objects::isNull).filter(<warning descr="Method reference result is always 'false'">Objects::nonNull</warning>).mapToInt(String::length).sum();
    if (<warning descr="Condition 'sum == 0' is always 'true'">sum == 0</warning>) {
      System.out.println("Always");
    }

    Optional<String> any = Stream.<String>empty().map(String::trim).findAny();
    if (<warning descr="Condition 'any.isPresent()' is always 'false'">any.isPresent()</warning>) {
      System.out.println("Never");
    }
    Optional<String> any2 = Stream.of("x").map(String::trim).findAny();
    if (<warning descr="Condition 'any2.isPresent()' is always 'true'">any2.isPresent()</warning>) {
      System.out.println("Always");
    }
    Optional<String> any3 = list.stream().map(String::trim).findAny();
    if (any3.isPresent()) {
      System.out.println("Possible");
    }
    Optional<String> any4 = Stream.of("x").limit(list.size()).map(String::trim).findAny();
    if (any4.isPresent()) {
      System.out.println("Probably");
    }

    boolean emptyAll = Stream.empty().allMatch(Objects::nonNull);
    if (<warning descr="Condition 'emptyAll' is always 'true'">emptyAll</warning>) {
      System.out.println("True");
    }
    boolean emptyAny = Stream.empty().anyMatch(Objects::nonNull);
    if (<warning descr="Condition 'emptyAny' is always 'false'">emptyAny</warning>) {
      System.out.println("False");
    }
    boolean emptyNone = Stream.empty().noneMatch(Objects::nonNull);
    if (<warning descr="Condition 'emptyNone' is always 'true'">emptyNone</warning>) {
      System.out.println("True");
    }

    boolean allMatch = Stream.generate(() -> "foo").limit(10).allMatch(<warning descr="Method reference result is always 'false'">"bar"::equals</warning>);
    if (allMatch) { // currently we're not aware that stream is non-empty (limit arg is not processed), and allMatch could be true for empty list
      System.out.println("Who knows?");
    }
    Optional<String> min = Stream.<String>empty().min(Comparator.comparing(String::length));
    if(<warning descr="Condition 'min.isPresent()' is always 'false'">min.isPresent()</warning>) {
      System.out.println("Impossible");
    }
  }

  void testSingleElement(String foo) {
    Optional<String> notNull = Stream.of(foo).filter(Objects::nonNull).findAny();
    if(<warning descr="Condition 'notNull.isPresent() && foo == null' is always 'false'">notNull.isPresent() && <warning descr="Condition 'foo == null' is always 'false' when reached">foo == null</warning></warning>) {
      System.out.println("Impossible");
    }
    OptionalInt max = IntStream.of(1).max();
    if(<warning descr="Condition 'max.isPresent()' is always 'true'">max.isPresent()</warning>) {
      System.out.println("Always");
    }
    OptionalLong opt = LongStream.of(2).reduce(Long::sum);
    if(<warning descr="Condition 'opt.isPresent()' is always 'true'">opt.isPresent()</warning>) {
      System.out.println("Always");
    }
  }

  void testStreamOf(int[] arr) {
    if(Stream.of().count() > 0) {
      System.out.println("Impossible");
    }
    if(<warning descr="Condition 'Stream.of(\"foo\", \"bar\").findFirst().isPresent()' is always 'true'">Stream.of("foo", "bar").findFirst().isPresent()</warning>) {
      System.out.println("Always");
    }
    if(<warning descr="Condition 'DoubleStream.of(0.0, 1.0, 2.0, 3.0).map(x -> x*2).max().isPresent()' is always 'true'">DoubleStream.of(0.0, 1.0, 2.0, 3.0).map(x -> x*2).max().isPresent()</warning>) {
      System.out.println("Always");
    }
    if(<warning descr="Condition 'IntStream.of(arr).filter(x -> x > 0).sum() > 0 && arr.length == 0' is always 'false'">IntStream.of(arr).filter(x -> x > 0).sum() > 0 && <warning descr="Condition 'arr.length == 0' is always 'false' when reached">arr.length == 0</warning></warning>) {
      System.out.println("Impossible");
    }
  }

  void testListSize(List<String> list, int[] arr) {
    if (<warning descr="Condition 'arr.length == 0 && Arrays.stream(arr).anyMatch(x -> x > 0)' is always 'false'">arr.length == 0 && <warning descr="Condition 'Arrays.stream(arr).anyMatch(x -> x > 0)' is always 'false' when reached">Arrays.stream(arr).anyMatch(x -> x > 0)</warning></warning>) {
      System.out.println("Impossible");
    }
    if (<warning descr="Condition 'Arrays.stream(arr).anyMatch(x -> x > 0) && arr.length == 0' is always 'false'">Arrays.stream(arr).anyMatch(x -> x > 0) && <warning descr="Condition 'arr.length == 0' is always 'false' when reached">arr.length == 0</warning></warning>) {
      System.out.println("Impossible");
    }
    boolean empty = list.isEmpty();
    String res = list.stream().map(String::trim).findFirst().orElse(null);
    if(res == null && <warning descr="Condition 'empty' is always 'true' when reached">empty</warning>) {
      System.out.println("res == null -> empty list");
    }
    Optional<String> first = list.stream().filter(Objects::nonNull).findFirst();
    if (empty) {
      System.out.println(first.<warning descr="'Optional.get()' without 'isPresent()' check">get</warning>());
    }
  }

  // IDEA-176129
  void foo(List<Long> list) {
    if (!list.isEmpty()) {
      return;
    }

    boolean hasNoNulls = list.stream().allMatch(Objects::nonNull);

    if(<warning descr="Condition 'hasNoNulls' is always 'true'">hasNoNulls</warning>) {
      System.out.println("Always");
    }
  }
}
