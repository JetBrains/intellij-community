import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class X {
  @NotNull
  public X fun1(int x) {
    return this;
  }

  public X fun2(@Nullable @SuppressWarnings("unused") String b) {
    <selection>
    if (b != null) {
      int x = 1;
      return fun1(x);
    }
    </selection>

    int x = 0;
    return null;
  }
}
