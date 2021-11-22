import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

public class OptionalAsQualifier {

  private static void example(@Nullable Container value) {
    Optional<Container> optional = Optional.ofNullable(value);
    if (optional.isPresent() && optional.get().getValue() != null) {
      foo(optional.get().getValue());
    }

    if (value != null) {
      if (value.getValue() != null) {
        foo(value.getValue());
      }
    }
  }

  private static void foo(@NotNull Object val) {}

  private static class Container {
    @Nullable private final Object val;

    private Container(@Nullable Object val) {
      this.val = val;
    }

    @Nullable
    public Object getValue() {
      return val;
    }
  }

}