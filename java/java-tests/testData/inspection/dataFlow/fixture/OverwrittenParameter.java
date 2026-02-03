import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class X {
  void test(@NotNull String string) {
    string = getString();
    if (string == null) {
    }
  }
  @Nullable
  String getString() {
    return null;
  }
}