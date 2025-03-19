// "Annotate overriding methods as '@NotNull'" "true"

import org.jetbrains.annotations.NotNull;

abstract class P2 {
  @NotNull<caret>
  String foo(@NotNull String p) {
    return p;
  }

  Object o = new P2() {
    @Override
    @NotNull
    String foo(String p) {
      return "";
    }
  };
}