// "Add missing nullability annotation to overriding methods" "true"

import org.jetbrains.annotations.NotNull;

abstract class P2 {
  @NotNull<caret>
  String foo(@NotNull String p) {
    return p;
  }

  Object o = new P2() {
    @Override
    String foo(String p) {
      return "";
    }
  };
}