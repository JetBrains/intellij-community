// "Fix all 'Constant conditions & exceptions' problems in file" "true"
import org.jetbrains.annotations.*;

import java.util.*;
import java.util.stream.Stream;

public class MethodReferenceConstantValue {
  @Contract(value = "!null -> false", pure = true)
  public boolean strangeMethod(String s) {
    return s == null ? new Random().nextBoolean() : false;
  }

  public void test(Optional<String> opt) {
    X x = MethodReferenceConstantValue::strangeMethod;
    Boolean aBoolean = opt.map(th<caret>is::strangeMethod)
      .map(Objects::nonNull)
      .map(Objects::isNull)
      .orElse(false);
    if (opt.isPresent()) {
      Stream.generate(opt::isPresent)
        .limit(10)
        .forEach(System.out::println);
    }
  }

  interface X {
    boolean action(@Nullable MethodReferenceConstantValue a, @NotNull String b);
  }
}