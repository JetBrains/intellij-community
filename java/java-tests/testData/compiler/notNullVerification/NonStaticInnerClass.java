import org.jetbrains.annotations.NotNull;

public class NonStaticInnerClass {
  public NonStaticInnerClass() {
    new Inner("");
  }

  public class Inner {
    public Inner(@NotNull String s) {
    }
  }
}
