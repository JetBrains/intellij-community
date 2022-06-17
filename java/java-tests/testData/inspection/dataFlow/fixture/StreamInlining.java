import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.*;
import java.util.stream.*;

public class StreamInlining {
  void testNulls(List<String> list) {
    list.stream().filter(<warning descr="Passing 'null' argument to parameter annotated as @NotNull">null</warning>).forEach(System.out::println);
    list.stream().map(<warning descr="Passing 'null' argument to parameter annotated as @NotNull">null</warning>).forEach(System.out::println);
    list.stream().flatMap(<warning descr="Passing 'null' argument to parameter annotated as @NotNull">null</warning>).forEach(System.out::println);
    list.stream().filter(x -> x != null).forEach(<warning descr="Passing 'null' argument to parameter annotated as @NotNull">null</warning>);
    List<String> l = null;
    l.<warning descr="Method invocation 'stream' will produce 'NullPointerException'">stream</warning>().count();
    int[] arr = null;
    Arrays.stream(<warning descr="Passing 'null' argument to parameter annotated as @NotNull">arr</warning>).count();
    Stream<String> stream = null;
    stream.<warning descr="Method invocation 'filter' will produce 'NullPointerException'">filter</warning>(x -> x != null).forEach(System.out::println);
  }

  void testMethodRef(List<String> list, int[] data) {
    if(<warning descr="Condition 'list.stream().map(String::new).anyMatch(x -> x == null)' is always 'false'">list.stream().map(String::new).anyMatch(x -> <warning descr="Condition 'x == null' is always 'false'">x == null</warning>)</warning>) {
      System.out.println("never");
    }
    if(<warning descr="Condition 'Arrays.stream(data).mapToObj(int[]::new).anyMatch(x -> x == null)' is always 'false'">Arrays.stream(data).mapToObj(int[]::new).anyMatch(x -> <warning descr="Condition 'x == null' is always 'false'">x == null</warning>)</warning>) {
      System.out.println("never");
    }
    list.stream().filter(Objects::isNull).map(<warning descr="Method reference invocation 'String::trim' may produce 'NullPointerException'">String::trim</warning>).forEach(System.out::println);
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
  }

  int hash(List<Holder> holders) {
    return holders.stream().filter(h -> h.obj == null).mapToInt(h -> h.obj.<warning descr="Method invocation 'hashCode' will produce 'NullPointerException'">hashCode</warning>()).sum();
  }

  void test2(int[] array) {
    IntStream.of(array).filter(x -> x < 5)
      .filter(x -> <warning descr="Condition 'x > 7' is always 'false'">x > 7</warning>)
      .forEach(value -> System.out.println(value));
  }

  void testInstanceof(List<?> objects) {
    objects.stream()
      .filter(x -> x instanceof String)
      .filter(x -> <warning descr="Condition 'x instanceof Number' is always 'false'">x instanceof Number</warning>)
      .forEach(System.out::println);
  }

  void testIsInstanceIncomplete(List<?> objects) {
    IntStream is = objects.stream()
      .filter(String.class::isInstance)
      .mapToInt(x -> (<warning descr="Casting 'x' to 'Integer' will produce 'ClassCastException' for any non-null value">Integer</warning>)x);

    objects.stream()
      .filter(String.class::isInstance)
      .filter(<warning descr="Method reference result is always 'false'">Number.class::isInstance</warning>);

    objects.stream()
      .filter(x -> x instanceof String)
      .filter(<warning descr="Method reference result is always 'false'">Number.class::isInstance</warning>);

    objects.stream()
      .filter(String.class::isInstance)
      .filter(<warning descr="Method reference result is always 'true'">String.class::isInstance</warning>);
  }

  Stream<String> testInstanceOfMap(List<?> objects) {
    return objects.stream().filter(it -> it instanceof String)
      .map(entry -> {
        if (<warning descr="Condition 'entry instanceof String' is always 'true'">entry instanceof String</warning>) {
          return (String)entry;
        }
        return null;
      })
      .filter(<warning descr="Method reference result is always 'true'">Objects::nonNull</warning>);
  }

  void test(Stream<String> stream, Optional<String> opt) {
    stream.filter(<warning descr="Condition 'String.class::isInstance' is redundant and can be replaced with a null check">String.class::isInstance</warning>).forEach(System.out::println);
    opt.filter(<warning descr="Method reference result is always 'true'">String.class::isInstance</warning>).ifPresent(System.out::println);
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
    return <warning descr="Result of 'list.stream().map(s -> s.isEmpty() ? null : s) .flatMap(s -> Stream.of(s, s.trim()) ...' is always 'false'">list.stream().map(s -> s.isEmpty() ? null : s)
               .flatMap(s -> Stream.of(s, s.<warning descr="Method invocation 'trim' may produce 'NullPointerException'">trim</warning>())
                    .filter(r -> <warning descr="Condition 'r != null' is always 'true'">r != null</warning>))
      .anyMatch(x -> <warning descr="Condition 'x == null' is always 'false'">x == null</warning>)</warning>;
  }

  String blockLambda(List<String> list) {
    return list == null ? "" : list.stream().map(s -> {
      return s.equals("abc") ? <warning descr="Function may return null, but it's not allowed here">null</warning> : s;
    }).findFirst().orElse("");
  }

  // IDEA-164262
  static class MyClass {
    @Nullable
    static String nullableFunction(String s) {
      return s.isEmpty() ? null : s;
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
      .limit(10).filter((xyz -> <warning descr="Result of '\"bar\".equals(xyz)' is always 'false'">"bar".equals(xyz)</warning>)).collect(Collectors.toList());
    List<String> list2 = Stream.generate(() -> "xyz").limit(20).filter(<warning descr="Method reference result is always 'false'">"bar"::equals</warning>).collect(Collectors.toList());
    Stream.generate(() -> Optional.of("xyz")).filter(<warning descr="Method reference result is always 'true'">Optional::isPresent</warning>).forEach(System.out::println);
    LongStream.generate(() -> 5).limit(10).filter(x -> <warning descr="Condition 'x > 6' is always 'false'">x > 6</warning>).forEach(s -> System.out.println(s));
  }

  // IDEA-183501
  void testFlatMapIdentity(Stream<Integer> stream) {
    Integer res = Stream.of(stream)
      .flatMap(Function.identity())
      .min(Comparator.naturalOrder())
      .orElse(null);
    if(res == null) { // not always null
      System.out.println("possible");
    }
  }

  // IDEA-190591
  void testReduce() {
    List<Double> input = new ArrayList<>();
    input.add(0.0);
    Optional<Double> result = input.stream().reduce((a, b) -> {
      throw new IllegalStateException("Multiple entries found: " + a + " and " + b);
    });
    Double res = result.orElse(null);
    if (<warning descr="Condition 'res != null' is always 'true'">res != null</warning>) {
      System.out.println(res);
    } else {
      System.out.println("Huh?");
    }
  }

  void testReduce2(List<Double> input) {
    Optional<Double> result = input.stream().reduce((a, b) -> {
      throw new IllegalStateException("Multiple entries found: " + a + " and " + b);
    });
    Double res = result.orElse(null);
    if (res != null) {
      System.out.println(res);
    } else {
      System.out.println("Huh?");
    }
  }

  void testReduceNullability() {
    Optional<String> res1 = Stream.of("foo", "bar", null).reduce((a, b) -> a); // a is never null - ok
    Optional<String> res2 = Stream.of("foo", null, "bar").reduce((a, b) -> a); // wrong, but not supported yet
    Optional<String> res3 = Stream.of("foo", "bar", null).reduce((a, b) -> <warning descr="Function may return null, but it's not allowed here">b</warning>);
    Optional<String> res4 = Stream.of(null, "foo", "bar").reduce((a, b) -> b); // b is never null - ok
  }

  public void testStreamTryFinally() {
    try {

    } finally {
      Stream.of("x").map(a -> {
        if(<warning descr="Condition 'a.equals(\"baz\")' is always 'false'">a.equals("baz")</warning>) {
          System.out.println("impossible");
        }
        testStreamTryFinally();
        return "bar";
      }).count();
    }
  }

  void testTryFinally2() {
    try {
    } finally {
      try {
        List<String> list = Stream.of("xyz").map(a -> {
          testTryFinally2();
          return "foo";
        }).collect(Collectors.toList());
      } catch (Exception e) {
      }
    }
  }

  void testToArray(List<String> list) {
    list.stream().toArray(size -> <warning descr="Function may return null, but it's not allowed here">null</warning>);
  }

  Object testBoxingExplicit() {
    return String.join("\n",
                Stream.of(12, 22)
                  .map(Object::toString)
                  .collect(Collectors.toList())
    );
  }

  public static void testBoxingExplicit2() {
    double s = Stream.of(1).mapToDouble(x -> x * x).sum();
  }
  
  void testOptionalNullity(List<Integer> groups) {
    Optional<Integer> optional = groups.stream().findFirst();
    if (<warning descr="Condition 'optional != null' is always 'true'">optional != null</warning> && optional.isPresent()) {
      System.out.println("found");
    }
  }
  
  void testImmediateCollection() {
    String result = Collections.singleton(" foo ").stream().map(String::trim).findFirst().orElse(null);
    if (<warning descr="Condition 'result == null' is always 'false'">result == null</warning>) {
      System.out.println("impossible");
    }
  }

  void testCountNarrowing(String[] arr, List<String> list) {
    long count1 = list.stream().filter(String::isEmpty).distinct().sorted().parallel().unordered().map(String::trim).count();
    if (<warning descr="Condition 'count1 > Integer.MAX_VALUE || count1 < 0' is always 'false'"><warning descr="Condition 'count1 > Integer.MAX_VALUE' is always 'false'">count1 > Integer.MAX_VALUE</warning> || <warning descr="Condition 'count1 < 0' is always 'false' when reached">count1 < 0</warning></warning>) {}
    long count2 = Arrays.stream(arr).map(String::trim).count();
    if (<warning descr="Condition 'count2 > Integer.MAX_VALUE || count2 < 0' is always 'false'"><warning descr="Condition 'count2 > Integer.MAX_VALUE' is always 'false'">count2 > Integer.MAX_VALUE</warning> || <warning descr="Condition 'count2 < 0' is always 'false' when reached">count2 < 0</warning></warning>) {}
    long count3 = Stream.of(arr).map(String::trim).count();
    if (<warning descr="Condition 'count3 > Integer.MAX_VALUE || count3 < 0' is always 'false'"><warning descr="Condition 'count3 > Integer.MAX_VALUE' is always 'false'">count3 > Integer.MAX_VALUE</warning> || <warning descr="Condition 'count3 < 0' is always 'false' when reached">count3 < 0</warning></warning>) {}
    if (count3 == 1) {}
    long count4 = Stream.of(arr[0]).map(String::trim).count();
    if (<warning descr="Condition 'count4 == 1' is always 'true'">count4 == 1</warning>) {}
    long count5 = Stream.of("foo", "bar", "baz", "qux").filter(s -> <warning descr="Condition 's.length() > 1' is always 'true'">s.length() > 1</warning>).count();
    long count5a = Stream.of("foo", "bar", "baz", "q").filter(s -> s.length() > 1).count();
    long count5b = Stream.of("foo", "bar", "bazzz", "qx").filter(s -> <warning descr="Condition 's.length() > 1' is always 'true'">s.length() > 1</warning>).count();
    if (count5 == 4) {}
    if (<warning descr="Condition 'count5 < 0' is always 'false'">count5 < 0</warning>) {}
    if (<warning descr="Condition 'count5 > 4' is always 'false'">count5 > 4</warning>) {}
    long count6 = Stream.of("foo", "bar", "baz", "qux").flatMap(s -> Stream.of(s, s)).count();
    if (<warning descr="Condition 'count6 < 0' is always 'false'">count6 < 0</warning>) {}
    if (count6 > 4) {}
    
    long count7 = Stream.of("foo", "bar").filter(x -> <warning descr="Condition '!x.isEmpty()' is always 'true'">!<warning descr="Result of 'x.isEmpty()' is always 'false'">x.isEmpty()</warning></warning>).count();
    if (<warning descr="Condition 'count7 == 0' is always 'false'">count7 == 0</warning>) {}
    if (<warning descr="Condition 'count7 == 1 || count7 == 2' is always 'true'">count7 == 1 || <warning descr="Condition 'count7 == 2' is always 'true' when reached">count7 == 2</warning></warning>) {}
    long count8 = <warning descr="Result of 'Stream.of(\"foo\", \"bar\").filter(x -> x.isEmpty()).count()' is always '0'">Stream.of("foo", "bar").filter(x -> <warning descr="Result of 'x.isEmpty()' is always 'false'">x.isEmpty()</warning>).count()</warning>;
    if (<warning descr="Condition 'count8 == 0' is always 'true'">count8 == 0</warning>) {}
    long count9 = list.stream().map(String::trim).count();
    if (count9 == 0) {}
    if (list.isEmpty()) return;
    long count10 = list.stream().map(String::trim).count();
    if (<warning descr="Condition 'count10 == 0' is always 'false'">count10 == 0</warning>) {}
  }

  void testFlatMapUnresolvedSymbol() {
    Stream.of("foo", "bar").flatMap(x -> Stream.of(
        <error descr="Cannot resolve symbol 'aa'">aa</error>, 
        <error descr="Cannot resolve symbol 'bb'">bb</error>, 
        <error descr="Cannot resolve symbol 'cc'">cc</error>));
  }

  void testNotTooComplexForEach(List<String> list) {
    int[] count = {0};
    list.stream().forEach(l -> count[0]++);
    System.out.println(count[0]);
  }
}
