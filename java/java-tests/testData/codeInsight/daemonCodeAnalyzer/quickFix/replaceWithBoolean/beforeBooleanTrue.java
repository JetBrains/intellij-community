// "Replace with 'Boolean.TRUE.equals(flag)'" "true"

import org.jetbrains.annotations.Nullable;

class Test {
  boolean test(@Nullable Boolean flag) {
    if (<caret>flag) {
      System.out.println("ok");
    }
    return true;
  }
}