// "Replace 'StringBuilder' with 'String'" "true"

import org.jetbrains.annotations.NotNull;

class Repeat {
  String foo(@NotNull String string) {
    StringBuilder <caret>sb = new StringBuilder();
    sb.repeat(string, 100);
    return sb.toString();
  }
}
