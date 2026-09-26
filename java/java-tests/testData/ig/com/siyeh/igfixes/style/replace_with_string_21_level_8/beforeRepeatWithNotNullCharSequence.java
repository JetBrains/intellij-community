// "Replace 'StringBuilder' with 'String'" "true"

import org.jetbrains.annotations.NotNull;

class Repeat {
  String foo(@NotNull CharSequence charSequence) {
    StringBuilder <caret>sb = new StringBuilder();
    sb.repeat(charSequence, 100);
    return sb.toString();
  }
}
