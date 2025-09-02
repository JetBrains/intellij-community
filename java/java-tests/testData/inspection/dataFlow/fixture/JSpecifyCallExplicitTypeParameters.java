import org.jspecify.annotations.NotNull;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class Demo {
  @NullMarked
  static class Test {
    static <T extends @Nullable Object> void test(T value) {
    }
  }

  void test() {
    Test.<@NotNull String>test(<warning descr="Passing 'null' argument to parameter annotated as @NotNull">null</warning>);
    Test.<@Nullable String>test(null);
  }
}