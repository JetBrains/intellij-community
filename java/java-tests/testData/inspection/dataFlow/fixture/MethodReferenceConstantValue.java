import org.jetbrains.annotations.*;

import java.util.*;
import java.util.stream.Stream;

public class MethodReferenceConstantValue {
  @Contract(value = "!null -> false", pure = true)
  public boolean strangeMethod(String s) {
    return s == null ? new Random().nextBoolean() : false;
  }

  public void test(Optional<String> opt) {
    X x = <warning descr="Method reference result is always 'false'">MethodReferenceConstantValue::strangeMethod</warning>;
    Boolean aBoolean = opt.map(<warning descr="Method reference result is always 'false'">this::strangeMethod</warning>)
      .map(<warning descr="Method reference result is always 'true'">Objects::nonNull</warning>)
      .map(<warning descr="Method reference result is always 'false'">Objects::isNull</warning>)
      .orElse(false);
    if (opt.isPresent()) {
      Stream.generate(<warning descr="Method reference result is always 'true'">opt::isPresent</warning>)
        .limit(10)
        .forEach(System.out::println);
    }
  }

  interface X {
    boolean action(@Nullable MethodReferenceConstantValue a, @NotNull String b);
  }
}