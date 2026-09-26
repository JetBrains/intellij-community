// "Replace with 'Boolean.TRUE.equals(flag)'" "false"

import org.jetbrains.annotations.Nullable;

class Test {
  boolean test(@NotNull Boolean flag) {
    if (<caret>flag) {
      System.out.println("ok");
    }
    return true;
  }
}