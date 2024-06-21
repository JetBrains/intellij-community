import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class Main {
  Record foo() {
    PsiFile file = fooBar();
    if (file instanceof PsiCompiledFile compiledFile) {
      return new Record(compiledFile);
    }
    return new Record(<warning descr="Argument 'file' might be null">file</warning>);
  }

  @Nullable
  private PsiFile fooBar() {
    return null;
  }

  interface PsiFile {}
  interface PsiCompiledFile extends PsiFile {}

  private record Record(@NotNull PsiFile file) {}
}