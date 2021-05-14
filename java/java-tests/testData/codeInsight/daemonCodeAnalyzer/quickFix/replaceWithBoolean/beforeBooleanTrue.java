// "Replace with 'Boolean.TRUE.equals(flag)'" "true"

import org.jetbrains.annotations.Nullable;

class Test {
  boolean test(@Nullable Boolean flag) {
    if (/*a*/<caret>flag/*b*/) {
      System.out.println("ok");
    }
    return true;
  }
}