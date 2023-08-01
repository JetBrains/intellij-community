package objects_require_non_null;

import org.jetbrains.annotations.NotNull;

class One {
  private String s;
  One(@NotNull String s) {
    this.s = <caret>s;
  }
}