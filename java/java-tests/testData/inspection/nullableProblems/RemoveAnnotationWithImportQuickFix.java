import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class RemoveAnnotationWithImportQuickFix {
  <warning descr="Cannot annotate with both @NotNull and @Nullable">@<caret>NotNull</warning> <warning descr="Cannot annotate with both @Nullable and @NotNull">@Nullable</warning> String s;
}