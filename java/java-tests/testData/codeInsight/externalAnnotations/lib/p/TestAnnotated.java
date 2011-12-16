package p;

import org.jetbrains.annotations.NotNull;

class Test {

  @NotNull
  String g<caret>et() {
    return null;
  }
}