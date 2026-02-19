import typeUse.*;

class NullMismatchArray {
  <warning descr="Cannot annotate with both @Nullable and @NotNull">@Nullable</warning>
  public <warning descr="Cannot annotate with both @NotNull and @Nullable">@NotNull</warning> String[] data;
}
