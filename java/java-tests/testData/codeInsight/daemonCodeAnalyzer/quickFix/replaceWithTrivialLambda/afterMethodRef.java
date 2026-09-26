// "Fix all 'Constant values' problems in file" "true"
import org.jetbrains.annotations.*;

import java.util.*;
import java.util.stream.Stream;

public class MethodReferenceConstantValue {
  @Contract(value = "!null -> false", pure = true)
  public boolean strangeMethod(String s) {
    return s == null ? new Random().nextBoolean() : false;
  }

  public void test(Optional<String> opt) {
    X x = (methodReferenceConstantValue, s1) -> false;
    Boolean aBoolean = opt.map(s -> false)
      .map(o1 -> true)
      .map(o -> false)
      .orElse(new Random().nextBoolean());
    if (opt.isPresent()) {
      Stream.generate(() -> true)
        .limit(10)
        .forEach(System.out::println);
    }
  }

  interface X {
    boolean action(@Nullable MethodReferenceConstantValue a, @NotNull String b);
  }
}