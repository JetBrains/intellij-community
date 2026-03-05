// "Replace 'StringBuilder' with 'String'" "true"

import org.jetbrains.annotations.NotNull;

class Repeat {
  String foo(@NotNull String string) {
      return string.repeat(100);
  }
}
