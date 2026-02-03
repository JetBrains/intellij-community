import typeUse.*;
import java.util.Objects;

class X {
  @NotNull String test() {
    return Objects.requireNonNull(getFoo());
  }
  
  native <T> @Nullable T getFoo();
}