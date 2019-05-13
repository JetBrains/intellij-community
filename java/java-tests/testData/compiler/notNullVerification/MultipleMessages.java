import org.jetbrains.annotations.NotNull;

public class MultipleMessages {
  @NotNull
  public Object foo1() {
    return null;
  }

  @NotNull
  public Object foo2() {
    return null;
  }

  public void bar1(@NotNull Object a) {
  }

  public void bar2(@NotNull Object b) {
  }
}