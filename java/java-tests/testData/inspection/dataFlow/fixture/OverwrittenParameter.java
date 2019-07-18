import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class X {
  void test(@NotNull String string) {
    string = fetchString();
    if (string == null) {
    }
  }
  @Nullable
  String fetchString() {
    return null;
  }
}