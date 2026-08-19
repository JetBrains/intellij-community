import org.jspecify.annotations.NotNull;
import org.jspecify.annotations.Nullable;
import org.jspecify.annotations.NullnessUnspecified;

class JSpecifyNullableParameterOverridesNotNull {
  interface Super {
    void useNotNull(@NotNull Object o);
  }

  interface Sub extends Super {
    // JSpecify deviates from the JLS here on purpose, see jspecify/jspecify#49
    @Override
    void useNotNull(@Nullable Object <warning descr="Parameter annotated @Nullable must not override @NotNull parameter">o</warning>);
  }
}
