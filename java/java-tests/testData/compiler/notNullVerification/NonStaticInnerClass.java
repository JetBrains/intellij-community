import org.jetbrains.annotations.NotNull;

public class NonStaticInnerClass {
  public NonStaticInnerClass() {
    new Inner(null, "");
  }

  public static void fail() {
    new NonStaticInnerClass().new Inner("", null);
  }

  public class Inner {
    public Inner(String s1, @NotNull String s2) { }
  }
}