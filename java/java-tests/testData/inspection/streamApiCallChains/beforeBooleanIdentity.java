// "Fix all 'Simplify stream API call chains' problems in file" "true"
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

public class Main {
  interface MyFunction extends Function<Object, Boolean> {}

  public static void main(String[] args) {
    MyFunction fn = "xyz"::equals;
    System.out.println(Stream.of("xyz").map(fn).any<caret>Match(Boolean::booleanValue));
  }

  public <T extends Boolean> boolean simpleFn(List<String> list, Function<String, T> fn) {
    return list.stream().map(fn).allMatch(Boolean::booleanValue);
  }


  public <T extends Boolean> boolean ternaryFn(List<String> list, Function<String, T> fn, Function<String, Boolean> fn2, boolean b) {
    return list.stream().map(b ? fn : fn2).allMatch(Boolean::booleanValue);
  }

  public <T extends Boolean> boolean doubleTernary(List<String> list, Function<String, T> fn, Function<String, Boolean> fn2, boolean b, boolean b2) {
    return list.stream().map(b ? fn : b2 ? fn2 : fn).allMatch(Boolean::booleanValue);
  }

  MyFunction fn3 = "xyz"::equals;

  public <T extends Boolean> boolean doubleTernaryOtherType(List<String> list, Function<String, T> fn, Function<String, Boolean> fn2, boolean b, boolean b2) {
    return list.stream().map(b ? fn : b2 ? fn2 : fn3).allMatch(Boolean::booleanValue);
  }

  public <T extends Boolean> boolean ternaryFnMrGeneric(List<String> list, Function<String, T> fn, boolean b) {
    return list.stream().map(b ? String::isEmpty : fn).allMatch(Boolean::booleanValue);
  }

  public boolean ternaryFnMrGenericComment(List<String> list, Function<String, Boolean> fn, boolean b) {
    return list.stream().map(b ? // select
                             /* comment */ String::isEmpty :
                             /* comment2 */fn).allMatch(Boolean::booleanValue);
  }

  public <T extends Boolean> boolean doubleTernaryMr(List<String> list, boolean b, boolean b2) {
    return list.stream().map(b ? String::isEmpty : b2 ? "foo"::equals : "bar"::equals)
      .allMatch(Boolean::booleanValue);
  }

  public boolean allMatchValueOf(List<String> list) {
    return list.stream().map(String::isEmpty).allMatch(b -> {
      return Boolean.valueOf(b);
    });
  }

  public boolean anyMatchBooleanValue(List<String> list) {
    return list.stream().map(String::isEmpty).anyMatch(/* ditto boolean!*/Boolean::booleanValue);
  }

  public boolean noneMatchDittoLambda(List<String> list) {
    return list.stream().map(String::isEmpty).noneMatch(b -> b);
  }
}