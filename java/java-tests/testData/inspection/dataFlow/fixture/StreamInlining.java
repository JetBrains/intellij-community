import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.*;

public class StreamInlining {
  void testNulls(List<String> list) {
    list.stream().filter(<warning descr="Passing 'null' argument to parameter annotated as @NotNull">null</warning>).forEach(System.out::println);
    list.stream().map(<warning descr="Passing 'null' argument to parameter annotated as @NotNull">null</warning>).forEach(System.out::println);
    list.stream().flatMap(<warning descr="Passing 'null' argument to parameter annotated as @NotNull">null</warning>).forEach(System.out::println);
    list.stream().filter(x -> x != null).forEach(<warning descr="Passing 'null' argument to parameter annotated as @NotNull">null</warning>);
    List<String> l = null;
    l.<warning descr="Method invocation 'stream' may produce 'java.lang.NullPointerException'">stream</warning>().count();
    int[] arr = null;
    Arrays.stream(<warning descr="Argument 'arr' might be null">arr</warning>).count();
    Stream<String> stream = null;
    stream.<warning descr="Method invocation 'filter' may produce 'java.lang.NullPointerException'">filter</warning>(x -> x != null).forEach(System.out::println);
  }

  void testMethodRef(List<String> list, int[] data) {
    if(<warning descr="Condition 'list.stream().map(String::new).anyMatch(x -> x == null)' is always 'false'">list.stream().map(String::new).anyMatch(x -> <warning descr="Condition 'x == null' is always 'false'">x == null</warning>)</warning>) {
      System.out.println("never");
    }
    if(<warning descr="Condition 'Arrays.stream(data).mapToObj(int[]::new).anyMatch(x -> x == null)' is always 'false'">Arrays.stream(data).mapToObj(int[]::new).anyMatch(x -> <warning descr="Condition 'x == null' is always 'false'">x == null</warning>)</warning>) {
      System.out.println("never");
    }
    list.stream().filter(Objects::isNull).map(<warning descr="Method reference invocation 'String::trim' may produce 'java.lang.NullPointerException'">String::trim</warning>).forEach(System.out::println);
    if(<warning descr="Condition 'list.stream().filter(Objects::nonNull).anyMatch(x -> x == null)' is always 'false'">list.stream().filter(Objects::nonNull).anyMatch(x -> <warning descr="Condition 'x == null' is always 'false'">x == null</warning>)</warning>) {
      System.out.println("never");
    }
    list.stream().map(Integer::valueOf).filter(<warning descr="Method reference result is always 'true'">Objects::nonNull</warning>).forEach(System.out::println);
  }

  void filterDistinctLimitSkipFilter(List<String> list) {
    list.stream().filter(x -> x == null).distinct().limit(10).skip(1).filter(x -> <warning descr="Condition 'x != null' is always 'false'">x != null</warning>).forEach(System.out::println);
  }

  static class Holder {
    Object obj;
    @Nullable String nullable;

    @Nullable String getNullable() {
      return nullable;
    }
  }

  int hash(List<Holder> holders) {
    return holders.stream().filter(h -> h.obj == null).mapToInt(h -> h.obj.<warning descr="Method invocation 'hashCode' may produce 'java.lang.NullPointerException'">hashCode</warning>()).sum();
  }

  void test2(int[] array) {
    IntStream.of(array).filter(x -> x < 5)
      .filter(x -> x > 7) // TODO: find variable state for non-qualified value
      .forEach(value -> System.out.println(value));
  }

  // IDEA-152871
  static class A {

    void improved(String[] arr) {
      Arrays.stream(arr).map(s -> s.isEmpty() ? s : null)
        .map(s1 -> some(<warning descr="Argument 's1' might be null">s1</warning>))
        .forEach(s -> System.out.println(s));
    }

    void improvedMethodRef(String[] arr) {
      Arrays.stream(arr).map(s -> s.isEmpty() ? s : null)
        .map(<warning descr="Method reference argument might be null">A::some</warning>)
        .forEach(s -> System.out.println(s));
    }

    public static String some(@NotNull String s) {
      return s;
    }
  }

  // IDEA-169280
  void f(List<String> l) {
    if(l.stream().filter(s -> s != null).allMatch(s -> <warning descr="Condition 's != null' is always 'true'">s != null</warning> && s.startsWith("A"))) {
      System.out.println("ok");
    }
  }

  void boxed(IntStream is) {
    is.boxed().filter(x -> <warning descr="Condition 'x != null' is always 'true'">x != null</warning>).forEach(s -> System.out.println(s));
  }

  boolean flatMap(List<String> list, List<List<String>> ll) {
    System.out.println(ll.stream().flatMap(l -> l.stream()).count());
    return list.stream().map(s -> s.isEmpty() ? null : s)
      .flatMap(s -> Stream.of(s, s.<warning descr="Method invocation 'trim' may produce 'java.lang.NullPointerException'">trim</warning>()).filter(r -> r != null))
      .anyMatch(x -> <warning descr="Condition 'x == null' is always 'false'">x == null</warning>);
  }

  String blockLambda(List<String> list) {
    return list == null ? "" : list.stream().map(s -> {
      return s.equals("abc") ? null : s;
    }).findFirst().orElse("");
  }

  // IDEA-164262
  static class MyClass {
    @Nullable
    static String nullableFunction(String s) {
      return s;
    }

    static String functionThatDoesNotAcceptNull(@NotNull String s) {
      return s;
    }

    void test(List<String> list) {
      list.stream()
        .map(MyClass::nullableFunction)
        .map(s -> functionThatDoesNotAcceptNull(<warning descr="Argument 's' might be null">s</warning>)) // Exception will be thrown here
        .forEach(System.out::println);
    }

    void testMr(List<String> list) {
      list.stream()
        .map(MyClass::nullableFunction)
        .map(<warning descr="Method reference argument might be null">MyClass::functionThatDoesNotAcceptNull</warning>)
        .forEach(System.out::println);
    }
  }

  Optional<String> testOptionalOfNullable(List<String> list) {
    return list.stream().filter(Objects::isNull).map(Optional::<warning descr="Passing 'null' argument to 'Optional'">ofNullable</warning>).findFirst().orElse(Optional.empty());
  }

  void testGenerate() {
    List<String> list1 = Stream.generate(() -> Math.random() > 0.5 ? "foo" : "baz")
      .limit(10).filter((xyz -> <warning descr="Condition '\"bar\".equals(xyz)' is always 'false'">"bar".equals(xyz)</warning>)).collect(Collectors.toList());
    List<String> list2 = Stream.generate(() -> "xyz").limit(20).filter(<warning descr="Method reference result is always 'false'">"bar"::equals</warning>).collect(Collectors.toList());
    Stream.generate(() -> Optional.of("xyz")).filter(<warning descr="Method reference result is always 'true'">Optional::isPresent</warning>).forEach(System.out::println);
    LongStream.generate(() -> 5).limit(10).filter(x -> <warning descr="Condition 'x > 6' is always 'false'">x > 6</warning>).forEach(s -> System.out.println(s));
  }

  @Nullable String process(String s) {
    return s.isEmpty() ? null : s;
  }

  void testMinMax(List<String> list) {
    list.stream().map(<warning descr="Function may return null, but it's not allowed here">this::process</warning>).max(Comparator.naturalOrder());
    list.stream().map(this::process).max(Comparator.nullsFirst(Comparator.naturalOrder()));
    list.stream().map(this::process).max(Comparator.nullsFirst(Comparator.comparing(String::length)));
    list.stream().map(this::process).min(Comparator.nullsLast(Comparator.comparing(String::length)));
    list.stream().map(this::process).min(Comparator.comparing(<warning descr="Method reference invocation 'String::length' may produce 'java.lang.NullPointerException'">String::length</warning>));
    list.stream().map(this::process).min(Comparator.comparing(<warning descr="Method reference invocation 'String::length' may produce 'java.lang.NullPointerException'">String::length</warning>).reversed());
  }

  void testSorted(List<String> list) {
    list.stream().sorted(Comparator.comparing(<warning descr="Function may return null, but it's not allowed here">this::process</warning>, Comparator.reverseOrder())).collect(Collectors.toList());
    list.stream().sorted(Comparator.comparing(<warning descr="Function may return null, but it's not allowed here">this::process</warning>)).collect(Collectors.toList());
    list.stream().sorted(Comparator.comparing(this::process, Comparator.nullsFirst(Comparator.naturalOrder()))).collect(Collectors.toList());
    list.stream().map(this::process).sorted(Comparator.comparing(<warning descr="Method reference invocation 'String::length' may produce 'java.lang.NullPointerException'">String::length</warning>)).collect(Collectors.toList());
    list.stream().map(<warning descr="Function may return null, but it's not allowed here">this::process</warning>).sorted().collect(Collectors.toList());
    list.stream().map(this::process).sorted(<warning descr="Method reference invocation 'String::compareToIgnoreCase' may produce 'java.lang.NullPointerException'">String::compareToIgnoreCase</warning>).collect(Collectors.toList());
    list.stream().map(<warning descr="Function may return null, but it's not allowed here">this::process</warning>).sorted(String.CASE_INSENSITIVE_ORDER).collect(Collectors.toList());
  }

  void testSortedCheck(List<Holder> holders) {
    holders.stream().sorted(Comparator.comparing(h -> <warning descr="Function may return null, but it's not allowed here">h.nullable</warning>)).toArray();
    holders.stream().filter(h -> h.nullable != null).sorted(Comparator.comparing(h -> h.nullable)).toArray();

    holders.stream().sorted(Comparator.comparing(h -> <warning descr="Function may return null, but it's not allowed here">h.getNullable()</warning>)).toArray();
    holders.stream().filter(h -> h.getNullable() != null).sorted(Comparator.comparing(h -> h.getNullable())).toArray();

    holders.stream().sorted(Comparator.comparing(<warning descr="Function may return null, but it's not allowed here">Holder::getNullable</warning>)).toArray();
    holders.stream().filter(h -> h.getNullable() != null).sorted(Comparator.comparing(Holder::getNullable)).toArray();
  }
}
