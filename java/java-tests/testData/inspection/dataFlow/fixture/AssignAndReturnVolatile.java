import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class Scratch {
  private volatile @Nullable String foo;

  public @NotNull String foo() {
    return this.foo = "bar";
  }
}