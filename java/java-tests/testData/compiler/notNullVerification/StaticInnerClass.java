import org.jetbrains.annotations.NotNull;

public class StaticInnerClass {
  public StaticInnerClass() {
    new Inner(null, "");
  }

  public static class Inner {
    public Inner(String s1, @NotNull String s2) { }
  }
}
