import org.jetbrains.annotations.NotNull;

public class AnnotatedNonStaticInnerClassConstructor {
  public class Inner {
    public Inner(@NotNull Integer param) { }

    public @NotNull String foo() {
      return "";
    }
  }
}