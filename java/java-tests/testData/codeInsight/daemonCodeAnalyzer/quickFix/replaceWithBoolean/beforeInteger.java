// "Replace with 'Boolean.TRUE.equals(flag)'" "false"

import org.jetbrains.annotations.Nullable;

class Test {
  int test(@Nullable Integer i) {
    return <caret>i;
  }
}