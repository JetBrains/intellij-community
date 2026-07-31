import org.jspecify.annotations.NotNull;
import org.jspecify.annotations.Nullable;
import org.jspecify.annotations.NullnessUnspecified;

class JSpecifyUnspecifiedParameterOverridesNullable {
  interface Super<T extends @Nullable Object> {
    void take(@NullnessUnspecified T t);
  }

  interface Sub extends Super<@Nullable String> {
    void take(@NotNull String <warning descr="Parameter annotated @NotNull must not override @NullnessUnspecified parameter">t</warning>);
  }

  interface SubHiddenBound<U extends @Nullable Object> extends Super<U> {
    void take(@NotNull U <warning descr="Parameter annotated @NotNull must not override @NullnessUnspecified parameter">t</warning>);
  }
}
