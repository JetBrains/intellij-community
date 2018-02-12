/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import java.util.*;

class OptionalWithoutIsPresent {

  void testSimple(Optional<String> o, OptionalDouble od, OptionalInt oi, OptionalLong ol) {
    System.out.println(o.<warning descr="'Optional.get()' without 'isPresent()' check">get</warning>());
    System.out.println(oi.<warning descr="'OptionalInt.getAsInt()' without 'isPresent()' check">getAsInt</warning>());
    System.out.println(ol.<warning descr="'OptionalLong.getAsLong()' without 'isPresent()' check">getAsLong</warning>());
    System.out.println(od.<warning descr="'OptionalDouble.getAsDouble()' without 'isPresent()' check">getAsDouble</warning>());
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

  void m(Optional<Integer> maybe) {
    if (!!!maybe.isPresent()) {
      maybe = getIntegerOptional();
    }
    else {
      System.out.println(maybe.get());
      maybe = getIntegerOptional();
    }
    if (maybe.isPresent()) {
      maybe = Optional.empty();
      System.out.println(maybe.<warning descr="The call to 'get' always fails, according to its method contracts">get</warning>());
    }
    boolean b = <warning descr="Condition '((maybe.isPresent()))' is always 'false'">((<warning descr="Condition 'maybe.isPresent()' is always 'false'">maybe.isPresent()</warning>))</warning> && maybe.get() == 1;
    boolean c = <warning descr="Condition '(!maybe.isPresent())' is always 'true'">(!<warning descr="Condition 'maybe.isPresent()' is always 'false'">maybe.isPresent()</warning>)</warning> || maybe.get() == 1;
    Integer value = <warning descr="Condition '!maybe.isPresent()' is always 'true'">!<warning descr="Condition 'maybe.isPresent()' is always 'false'">maybe.isPresent()</warning></warning> ? 0 : maybe.get();
  }

  Optional<Integer> getIntegerOptional() {
    return Math.random() > 0.5 ? Optional.of(1) : Optional.empty();
  }

  private static void a() {
    Optional<String> optional = Optional.empty();
    final boolean present = <warning descr="Condition 'optional.isPresent()' is always 'false'">optional.isPresent()</warning>;
    // optional = Optional.empty();
    if (<warning descr="Condition 'present' is always 'false'">present</warning>) {
      final String string = optional.get();
      System.out.println(string);
    }
  }

  private static void b() {
    Optional<String> optional = Optional.empty();
    final boolean present = <warning descr="Condition 'optional.isPresent()' is always 'false'">optional.isPresent()</warning>;
    optional = Optional.empty();
    if (<warning descr="Condition 'present' is always 'false'">present</warning>) {
      final String string = optional.get();
      System.out.println(string);
    }
  }

  public void testMultiVars(Optional<String> opt) {
    boolean present = opt.isPresent();
    boolean absent = !present;
    boolean otherAbsent = !!absent;
    if(otherAbsent) {
      System.out.println(opt.<warning descr="The call to 'get' always fails, according to its method contracts">get</warning>());
    } else {
      System.out.println(opt.get());
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

    o2 = getOptional();
    org.junit.Assert.assertTrue(!o2.isPresent());
    System.out.println(o2.<warning descr="The call to 'get' always fails, according to its method contracts">get</warning>());
  }

  private void checkAsserts2() {
    Optional<String> o3 = Optional.empty();
    org.testng.Assert.<warning descr="The call to 'assertTrue' always fails, according to its method contracts">assertTrue</warning>(<warning descr="Condition 'o3.isPresent()' is always 'false'">o3.isPresent()</warning>);
    System.out.println(o3.get());
  }

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
    if(<warning descr="Condition 'b' is always 'true'">b</warning>) {
      test = Optional.empty();
    } else {
      test = Optional.empty();
    }
    System.out.println(test.<warning descr="The call to 'get' always fails, according to its method contracts">get</warning>());

  }

  private void checkOfNullable(String value) {
    System.out.println(Optional.ofNullable(value).<warning descr="'Optional.get()' without 'isPresent()' check">get</warning>());
    System.out.println(Optional.ofNullable(<warning descr="Passing a non-null argument to 'Optional'">value+"a"</warning>).get());
    System.out.println(Optional.ofNullable(<warning descr="Passing a non-null argument to 'Optional'">"xyz"</warning>).get());
  }

  public static String demo() {
    Optional<String> holder = Optional.empty();

    if (<warning descr="Condition '! holder.isPresent()' is always 'true'">! <warning descr="Condition 'holder.isPresent()' is always 'false'">holder.isPresent()</warning></warning>) {
      holder = Optional.of("hello world");
      if (<warning descr="Condition '!holder.isPresent()' is always 'false'">!<warning descr="Condition 'holder.isPresent()' is always 'true'">holder.isPresent()</warning></warning>) {
        return null;
      }
    }

    return holder.get();
  }

  //public Collection<String> findCategories(Long articleId) {
  //  return java.util.stream.Stream.of(Optional.of("asdf")).filter(Optional::isPresent).map(x -> x.get()).collect(
  //    java.util.stream.Collectors.toList()) ;
  //}

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
    order.ifPresent(o -> System.out.println(order.get()));
    System.out.println(order.orElseGet(() -> order.<warning descr="The call to 'get' always fails, according to its method contracts">get</warning>().trim()));
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
    if (<warning descr="Condition 'true || o.isPresent()' is always 'true'">true || o.isPresent()</warning>) {
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
    if(<warning descr="Condition 'opt.isPresent()' is always 'true'">opt.isPresent()</warning>) {
      System.out.println(opt.get());
    }
    opt = com.google.common.base.Optional.fromNullable(s);
    if(opt.isPresent()) {
      System.out.println(opt.get());
    }
    opt = com.google.common.base.Optional.of(<warning descr="Argument 's' might be null">s</warning>);
    opt = com.google.common.base.Optional.of(s1);
    if(<warning descr="Condition 'opt.isPresent()' is always 'true'">opt.isPresent()</warning>) {
      System.out.println(opt.get());
    }
    opt = com.google.common.base.Optional.absent();
    if(<warning descr="Condition 'opt.isPresent()' is always 'false'">opt.isPresent()</warning>) {
      System.out.println(opt.get());
    }
  }

  void testThrow(Optional<String> test) {
    test.orElseThrow(RuntimeException::new);
    Object o = test.get();
    System.out.println(o);
  }

  void testThrow2(Optional<String> test) {
    test.orElseThrow(RuntimeException::new);
    if (<warning descr="Condition 'test.isPresent()' is always 'true'">test.isPresent()</warning>) {
      System.out.println("Yes");
    }
  }

  void testThrowCatch(Optional<String> opt) {
    try {
      opt.orElseThrow(RuntimeException::new);
      System.out.println("Ok: " + opt.get());
    } catch (RuntimeException ex) {
      System.out.println("Fail: " + opt.<warning descr="'Optional.get()' without 'isPresent()' check">get</warning>());
    }
  }

  public void testThrowFail(Optional<String> arg) {
    if(!arg.isPresent()) {
      System.out.println(arg.<warning descr="The call to 'orElseThrow' always fails, according to its method contracts">orElseThrow</warning>(IllegalAccessError::new));
    }
    String res = Optional.<String>empty().<warning descr="The call to 'orElseThrow' always fails, according to its method contracts">orElseThrow</warning>(RuntimeException::new);
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
}