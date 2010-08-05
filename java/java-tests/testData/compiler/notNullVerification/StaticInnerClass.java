import org.jetbrains.annotations.NotNull;

public class StaticInnerClass {
  public StaticInnerClass() {
    new Inner("");
  }

  public static class Inner {
    public Inner(@NotNull String s) {
    }
  }
}
