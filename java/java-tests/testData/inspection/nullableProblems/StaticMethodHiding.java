import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class StaticMethodHiding {
  static class Parent {
    public static @NotNull Parent create() { return new Parent(); }
  }
  static class Child extends Parent {
    public static @Nullable Child create() { return null; }
  }
}
