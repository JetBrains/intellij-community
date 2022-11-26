import org.jetbrains.annotations.*;

import java.util.*;
import java.util.stream.Stream;

public class MethodReferenceConstantValue {

  void testNonAnnotated() {
    nonAnnotated(<warning descr="Argument 'nullable()' might be null but passed to non-annotated parameter">nullable()</warning>);
    Stream.of(nullable()).map(<warning descr="Method reference argument might be null but passed to non-annotated parameter">MethodReferenceConstantValue::nonAnnotated</warning>);
  }

  @Contract(value = "!null -> false", pure = true)
  public boolean strangeMethod(String s) {
    return s == null ? new Random().nextBoolean() : false;
  }

  @Contract(value = "_ -> true", pure = true)
  private static boolean alwaysTrue(Object x) {
    return true;
  }

  public void test(Optional<String> opt) {
    X x = <warning descr="Method reference invocation 'MethodReferenceConstantValue::strangeMethod' may produce 'NullPointerException'"><warning descr="Method reference result is always 'false'">MethodReferenceConstantValue::strangeMethod</warning></warning>;
    Boolean aBoolean = opt.map(<warning descr="Method reference result is always 'false'">this::strangeMethod</warning>)
      .map(<warning descr="Method reference result is always 'true'">Objects::nonNull</warning>)
      .map(<warning descr="Method reference result is always 'false'">Objects::isNull</warning>)
      .map(MethodReferenceConstantValue::alwaysTrue)
      .orElse(false);
    if (opt.isPresent()) {
      Stream.generate(<warning descr="Method reference result is always 'true'">opt::isPresent</warning>)
        .limit(10)
        .forEach(System.out::println);
    }
  }
  
  public static native String nonAnnotated(String s);
  @Nullable
  public static native String nullable();

  public void test(List<@foo.NotNull String> list) {
    list.removeIf(<warning descr="Method reference result is always 'false'">Objects::isNull</warning>);
  }

  interface X {
    boolean action(@Nullable MethodReferenceConstantValue a, @NotNull String b);
  }
}