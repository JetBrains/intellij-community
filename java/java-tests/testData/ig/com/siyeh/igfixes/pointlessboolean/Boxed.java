import org.jetbrains.annotations.NotNull;

class Boxed {
  void f(@NotNull Boolean b) {
    if (<caret>b == (false)) {}
  }
}
