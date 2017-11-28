// "Fix all 'Simplify stream API call chains' problems in file" "true"
import java.util.*;
import java.util.function.Function;
import java.util.stream.Stream;

public class Main {
  interface MyFunction extends Function<Object, Boolean> {}

  public static void main(String[] args) {
    MyFunction fn = "xyz"::equals;
    System.out.println(Stream.of("xyz").anyMatch(fn::apply));
  }

  public <T extends Boolean> boolean simpleFn(List<String> list, Function<String, T> fn) {
    return list.stream().allMatch(fn::apply);
  }


  public <T extends Boolean> boolean ternaryFn(List<String> list, Function<String, T> fn, Function<String, Boolean> fn2, boolean b) {
    return list.stream().allMatch(b ? fn::apply : fn2::apply);
  }

  public <T extends Boolean> boolean doubleTernary(List<String> list, Function<String, T> fn, Function<String, Boolean> fn2, boolean b, boolean b2) {
    return list.stream().allMatch(b ? fn::apply : b2 ? fn2::apply : fn::apply);
  }

  MyFunction fn3 = "xyz"::equals;

  public <T extends Boolean> boolean doubleTernaryOtherType(List<String> list, Function<String, T> fn, Function<String, Boolean> fn2, boolean b, boolean b2) {
    return list.stream().allMatch(b ? fn::apply : b2 ? fn2::apply : fn3::apply);
  }

  public <T extends Boolean> boolean ternaryFnMrGeneric(List<String> list, Function<String, T> fn, boolean b) {
    return list.stream().allMatch(b ? String::isEmpty : fn::apply);
  }

  public boolean ternaryFnMrGenericComment(List<String> list, Function<String, Boolean> fn, boolean b) {
    return list.stream().allMatch(b ? // select
            /* comment */ String::isEmpty :
            /* comment2 */fn::apply);
  }

  public <T extends Boolean> boolean doubleTernaryMr(List<String> list, boolean b, boolean b2) {
    return list.stream().allMatch(b ? String::isEmpty : b2 ? "foo"::equals : "bar"::equals);
  }

  public boolean allMatchValueOf(List<String> list) {
    return list.stream().allMatch(String::isEmpty);
  }

  public boolean anyMatchBooleanValue(List<String> list) {
      /* ditto boolean!*/
      return list.stream().anyMatch(String::isEmpty);
  }

  public boolean noneMatchDittoLambda(List<String> list) {
    return list.stream().noneMatch(String::isEmpty);
  }

  public boolean anyMatchBooleanEquals(List<String> list) {
    return list.stream().anyMatch(String::isEmpty);
  }

  public boolean anyMatchBooleanEqualsFalse(List<String> list) {
    return list.stream().map(String::isEmpty).anyMatch(Boolean.FALSE::equals);
  }

  public boolean anyMatchBooleanEqualsLambda(List<String> list) {
    return list.stream().anyMatch(String::isEmpty);
  }

  public boolean allMatchBooleanEqualsLambda(List<String> list) {
    return list.stream().noneMatch(String::isEmpty);
  }

  public boolean noneMatchBooleanEqualsLambda(List<String> list) {
    return list.stream().map(String::isEmpty).noneMatch(b -> Boolean.FALSE.equals((!b)));
  }

  interface Source {
    Optional<Boolean> containsDescendantOf(String name);
  }

  public static boolean testNullityInference(Stream<Source> sources, String name) {
    return sources.noneMatch((s) -> s.containsDescendantOf(name).orElse(true));
  }

  public static boolean testNullityInference2(Stream<Source> sources, String name) {
    return sources.map((s) -> s.containsDescendantOf(name).orElse(null)).allMatch(Boolean.FALSE::equals);
  }
}