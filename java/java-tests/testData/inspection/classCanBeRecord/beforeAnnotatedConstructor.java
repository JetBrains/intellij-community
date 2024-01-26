// "Convert to record class" "true"
import org.jetbrains.annotations.NotNull;

public final class <caret>Box {
  private final @NotNull Object object;

  public Box(@NotNull Object object) {
    this.object = object;
  }

  public @NotNull Object object() {
    return object;
  }

}