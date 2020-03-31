import org.jetbrains.annotations.*;

class Test {
  <warning descr="Not-null fields must be initialized">@NotNull</warning> String explicit;
  @NotNull String implicit;
}