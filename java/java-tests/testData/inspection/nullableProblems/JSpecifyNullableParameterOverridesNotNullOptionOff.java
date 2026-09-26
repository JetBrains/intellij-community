import org.jspecify.annotations.NotNull;
import org.jspecify.annotations.Nullable;

class JSpecifyNullableParameterOverridesNotNullOptionOff {
  interface Super {
    void useNotNull(@NotNull Object o);
  }

  interface Sub extends Super {
    @Override
    void useNotNull(@Nullable Object o);
  }
}
