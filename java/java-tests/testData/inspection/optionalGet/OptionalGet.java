import java.util.*;
import java.util.stream.Stream;

class OptionalGet {
  private void checkOf(boolean b) {
    System.out.println(Optional.of("xyz").get());
    Optional<String> test;
    if(b) {
      test = Optional.of("x");
    } else {
      test = Optional.of("y");
    }
    System.out.println(test.get());
    if(b) {
      test = Optional.of("x");
    } else {
      test = Optional.empty();
    }
    System.out.println(test.<warning descr="'Optional.get()' without 'isPresent()' check">get</warning>());

  }

  private void checkOfNullable(String value) {
    System.out.println(Optional.ofNullable(value).<warning descr="'Optional.get()' without 'isPresent()' check">get</warning>());
    System.out.println(Optional.ofNullable(value+"a").get());
    System.out.println(Optional.ofNullable("xyz").get());
  }

  void testSimple(Optional<String> o, OptionalDouble od, OptionalInt oi, OptionalLong ol) {
    System.out.println(o.<warning descr="'Optional.get()' without 'isPresent()' check">get</warning>());
    System.out.println(oi.<warning descr="'OptionalInt.getAsInt()' without 'isPresent()' check">getAsInt</warning>());
    System.out.println(ol.<warning descr="'OptionalLong.getAsLong()' without 'isPresent()' check">getAsLong</warning>());
    System.out.println(od.<warning descr="'OptionalDouble.getAsDouble()' without 'isPresent()' check">getAsDouble</warning>());
  }

  void testParentheses(Optional<String> o, String value, String value2) {
    System.out.println((o).<warning descr="'Optional.get()' without 'isPresent()' check">get</warning>());
    System.out.println((Optional.ofNullable(value)).<warning descr="'Optional.get()' without 'isPresent()' check">get</warning>());
    System.out.println((Optional.of(value)).get());
    System.out.println((Optional.ofNullable("foo")).get());
    System.out.println((Optional.of("foo")).get());
  }

  void testTernary(Optional<String> foo, Optional<String> bar, boolean b) {
    if(bar.isPresent()) {
      if(foo.isPresent()) {
        System.out.println((b ? foo : bar).get());
      }
      System.out.println((b ? foo : bar).<warning descr="'Optional.get()' without 'isPresent()' check">get</warning>());
    }
    if(foo.isPresent()) {
      System.out.println((b ? foo : bar).<warning descr="'Optional.get()' without 'isPresent()' check">get</warning>());
    }
    System.out.println((b ? foo : bar).<warning descr="'Optional.get()' without 'isPresent()' check">get</warning>());
  }

  {
    System.out.println(getIntegerOptional().<warning descr="'Optional.get()' without 'isPresent()' check">get</warning>());
  }

  void testWhile() {
    Optional<String> o = Optional.empty();
    while (!o.isPresent()) {
      o = Optional.of("");
    }
    System.out.println(o.get());
  }

  void testWhile2() {
    Optional<Integer> o = getIntegerOptional();
    while (o.isPresent()) {
      System.out.println(o.get());
    }
  }

  public void testPolyadicExpression(Optional<String> value) {
    boolean flag = value.isPresent() && "Yes".equals(value.get());
  }

  boolean testPolyadicExpression2(Optional<String> o) {
    return !o.isPresent() || o.get().equals("j");
  }

  String testPolyadicExpression3() {
    Optional<String> o = getOptional();
    if (o == null || !o.isPresent()) {
      return "";
    }
    return o.get();
  }

  void testNested(Optional<String> opt, String action) {
    if (!opt.isPresent()) {
      throw new IllegalArgumentException();
    }
    switch (action) {
      case "case":
        System.out.println(opt.get());
        break;
      default:
        System.err.println(opt.get());
    }
  }

  Optional<Integer> getIntegerOptional() {
    return Math.random() > 0.5 ? Optional.of(1) : Optional.empty();
  }

  private static void a() {
    Optional<String> optional = Optional.empty();
    final boolean present = optional.isPresent();
    // optional = Optional.empty();
    if (present) {
      // do not warn here as the branch is unreachable
      final String string = optional.get();
      System.out.println(string);
    }
  }

  private static void b() {
    Optional<String> optional = Optional.empty();
    final boolean present = optional.isPresent();
    optional = Optional.empty();
    if (present) {
      // do not warn here as the branch is unreachable
      final String string = optional.get();
      System.out.println(string);
    }
  }

  private void checkReassign(Optional<String> a, Optional<String> b) {
    if(a.isPresent()) {
      b = a;
      System.out.println(b.get());
    }
  }

  private void checkReassign2(Optional<String> a, Optional<String> b) {
    if(b.isPresent()) {
      b = a;
      System.out.println(b.<warning descr="'Optional.get()' without 'isPresent()' check">get</warning>());
    }
  }

  private void checkAsserts1() {
    Optional<String> o1 = getOptional();
    assert o1.isPresent();
    System.out.println(o1.get());
    Optional<String> o2 = getOptional();
    org.junit.Assert.assertTrue(o2.isPresent());
    System.out.println(o2.get());
    Optional<String> o3 = getOptional();
    org.testng.Assert.assertTrue(o3.isPresent());
    System.out.println(o3.get());
  }

  public Collection<String> findCategories(Long articleId) {
    return java.util.stream.Stream.of(Optional.of("asdf")).filter(Optional::isPresent).map(x -> x.get()).collect(
      java.util.stream.Collectors.toList()) ;
  }

  public static void main(String[] args) {
    Optional<String> stringOpt;

    if((stringOpt = getOptional()).isPresent()) {
      stringOpt.get();
    }
  }

  public static void main2(String[] args) {
    if(getOptional().isPresent()) {
      getOptional().get(); //'Optional.get()' without 'isPresent()' check
    }
  }

  public static Optional<String> getOptional() {
    return Optional.empty();
  }

  void order(Optional<String> order, boolean b) {
    // here order.get always suceeds
    order.ifPresent(o -> System.out.println(order.get()));
    // here order.get always fails: tested in normal DFA
    System.out.println(order.orElseGet(() -> order.get().trim()));
  }

  public static void two(Optional<Object> o1,Optional<Object> o2) {
    if (!o1.isPresent() && !o2.isPresent()) {
      return;
    }
    System.out.println(o1.isPresent() ? o1.get() : o2.get());
  }

  public static void two2(Optional<Object> o1,Optional<Object> o2) {
    if (!o2.isPresent()) {
      return;
    }
    System.out.println(o1.isPresent() ? o1.get() : o2.get());
  }

  public static void two3(Optional<Object> o1,Optional<Object> o2) {
    System.out.println(o1.isPresent() ? o1.get() : o2.<warning descr="'Optional.get()' without 'isPresent()' check">get</warning>());
  }

  void def(Optional<String> def) {
    if (!def.isPresent()) {
      throw new RuntimeException();
    }
    {
      def.get();
    }
  }

  private void orred(Optional<Integer> opt1, Optional<Integer> opt2) {
    if (!opt1.isPresent() || !opt2.isPresent()) {
      return;
    }
    opt1.get();
    opt2.get();
  }

  class Range {
    Optional<Integer> min = Optional.of(1);
    Optional<Integer> max = Optional.of(2);

    Optional<Integer> getMax() {
      return max;
    }
    Optional<Integer> getMin() {
      return min;
    }
  }
  void useRange(Range a, Range b) {
    if (!a.getMax().isPresent() || !b.getMin().isPresent()) {

    }
    else if (a.getMax().get() <= b.getMin().get()) {

    }
  }

  public void foo(Optional<Long> value) {
    if (!value.isPresent()) {
      return;
    }

    try {
      System.out.println(value.get()); // <- warning appears here
    } finally {
      System.out.println("hi");
    }
  }

  void shortIf(Optional<String> o) {
    if (true || o.isPresent()) {
      o.<warning descr="'Optional.get()' without 'isPresent()' check">get</warning>();
    }
  }

  void test(Optional<String> aOpt, Optional<String> bOpt) {
    if (!aOpt.isPresent() || !bOpt.isPresent()) {
      throw new RuntimeException();
    }
    String a = aOpt.get();
    String b = bOpt.get();
  }

  String f(Optional<String> optional, Optional<String> opt2) {
    return optional.isPresent()  ? opt2.<warning descr="'Optional.get()' without 'isPresent()' check">get</warning>() : "";
  }

  com.google.common.base.Optional<String> field;

  void guavaFieldTest() {
    if(field.isPresent()) {
      System.out.println(field.get());
    }
  }

  void guavaTest(com.google.common.base.Optional<String> opt, String s, String s1) {
    System.out.println(opt.<warning descr="'Optional.get()' without 'isPresent()' check">get</warning>());
    if(opt.isPresent()) {
      System.out.println(opt.get());
    }
    opt = com.google.common.base.Optional.fromNullable(s);
    if(opt.isPresent()) {
      System.out.println(opt.get());
    }
    opt = com.google.common.base.Optional.of(s);
    opt = com.google.common.base.Optional.of(s1);
    if(opt.isPresent()) {
      System.out.println(opt.get());
    }
    opt = com.google.common.base.Optional.absent();
    if(opt.isPresent()) {
      System.out.println(opt.get());
    }
  }

  void testThrow(Optional<String> test) {
    test.orElseThrow(RuntimeException::new);
    Object o = test.get();
    System.out.println(o);
  }

  void testThrowCatch(Optional<String> opt) {
    try {
      opt.orElseThrow(RuntimeException::new);
      System.out.println("Ok: " + opt.get());
    } catch (RuntimeException ex) {
      System.out.println("Fail: " + opt.<warning descr="'Optional.get()' without 'isPresent()' check">get</warning>());
    }
  }

  void testOrElseGet() {
    final Optional<String> a = Optional.ofNullable(Math.random() > 0.5 ? null:"");
    final Optional<String> b = Optional.ofNullable(Math.random() > 0.5 ? null:"");
    if (a.isPresent() || b.isPresent()) {
      String result = a.orElseGet(() -> b.get()); // no warning
      System.out.println(result);
    }
    String result = a.orElseGet(() -> b.<warning descr="'Optional.get()' without 'isPresent()' check">get</warning>());
    System.out.println(result);
  }

  boolean testBooleanOptional(Optional<Boolean> opt) {
    if (opt.isPresent() && !opt.get()) {
      return false;
    }
    return true;
  }

  void testArrayStream(int[] arr1, int[] arr2) {
    if(arr1.length == 0) return;
    System.out.println(Arrays.stream(arr1).map(Math::abs).min().getAsInt());
    System.out.println(Arrays.stream(arr2).map(Math::abs).min().<warning descr="'OptionalInt.getAsInt()' without 'isPresent()' check">getAsInt</warning>());
  }

  public String getFirstItem(Collection<String> data) {
    return data.stream().findFirst().<warning descr="'Optional.get()' without 'isPresent()' check">get</warning>();
  }

  public String getFirstItemChecked(Collection<String> data) {
    if(data.isEmpty()) throw new IllegalArgumentException("Data should never be empty");
    // Non-empty stream: get() is fine
    return data.stream().findFirst().get();
  }

  public String getMax() {
    // Non-empty stream: get() is fine
    return Stream.of("foo", "bar", "baz").map(String::toUpperCase).max(Comparator.naturalOrder()).get();
  }

  void testStreamOfUnrolling(Optional<String> optionalOne, Optional<String> optionalTwo, Optional<String> optionalThree) {
    if (Stream.of(optionalOne, optionalTwo).allMatch(Optional::isPresent)) {
      System.out.println(optionalOne.get());
      System.out.println(optionalTwo.get());
      System.out.println(optionalThree.<warning descr="'Optional.get()' without 'isPresent()' check">get</warning>());
    }
  }
}

class CtorTest {
  Optional<String> test = Optional.of("foo");

  CtorTest() {
    System.out.println(test.get());
    something();
    System.out.println(test.<warning descr="'Optional.get()' without 'isPresent()' check">get</warning>());
  }

  <error descr="Missing method body, or declare abstract">CtorTest(String noBody);</error>

  void something() {
    test = Optional.empty();
  }
}