// "Replace 'StringBuilder' with 'String'" "true"

import org.jetbrains.annotations.NotNull;

class Repeat {
  String foo(@NotNull CharSequence charSequence) {
      return charSequence.toString().repeat(100);
  }
}
