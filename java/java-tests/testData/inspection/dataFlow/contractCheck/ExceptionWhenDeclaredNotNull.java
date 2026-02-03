import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class Foo {

  @Contract(value = "_, true -> !null")
  public @Nullable String noNotAllowed(@NotNull String bar, boolean reallyNotAllowed) {
    if (bar.equals("no")) {
      if (reallyNotAllowed) {
        throw new IllegalArgumentException("heck no");
      }
      return null;
    }
    return bar;
  }
}
