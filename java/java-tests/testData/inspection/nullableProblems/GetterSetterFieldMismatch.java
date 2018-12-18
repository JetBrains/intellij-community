import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class NotNullGetterTest {

  @NotNull
  private final Object foo;

  @Nullable
  private Object bar;

  public NotNullGetterTest(@NotNull Object foo, @Nullable Object bar) {
    this.foo = foo;
    this.bar = bar;
  }

  @NotNull
  public Object getFoo() {
    return foo;
  }

  @NotNull
  public Object getBar() {
    return foo;
  }

  void setBar(@NotNull Object bar) {
    this.bar = bar;
  }

  void setFoo(@NotNull Object bar) {
    this.bar = bar;
  }
}