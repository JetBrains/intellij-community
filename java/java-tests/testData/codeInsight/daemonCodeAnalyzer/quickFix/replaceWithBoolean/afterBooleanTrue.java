// "Replace with 'Boolean.TRUE.equals(flag)'" "true"

import org.jetbrains.annotations.Nullable;

class Test {
  boolean test(@Nullable Boolean flag) {
    if (Boolean.TRUE.equals(flag)) {
      System.out.println("ok");
    }
    return true;
  }
}