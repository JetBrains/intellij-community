import org.jetbrains.annotations.*;

public class TypeUseAndMemberAnnotationsOnArrays {

  public void nullableArray(@NotNull String @Nullable [] query) {}

  public void notNullArray(@Nullable String @NotNull [] query) {}

  public @Nullable String @NotNull [] notNullReturn() {
    return null;
  }

  public @NotNull String @Nullable [] nullableReturn() {
    return null;
  }
}