// "Replace with 'Boolean.TRUE.equals(flag)'" "true-preview"

import org.jetbrains.annotations.Nullable;

class Test {
  boolean test(@Nullable Boolean flag) {
    if (/*a*/Boolean.TRUE.equals(flag)/*b*/) {
      System.out.println("ok");
    }
    return true;
  }
}