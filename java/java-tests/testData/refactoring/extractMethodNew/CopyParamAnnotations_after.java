import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class X {
  @NotNull
  public X fun1(int x) {
    return this;
  }

  public X fun2(@Nullable @SuppressWarnings("unused") String b) {

      X x1 = newMethod(b);
      if (x1 != null) return x1;


      int x = 0;
    return null;
  }

    @Nullable
    private X newMethod(@Nullable String b) {
        if (b != null) {
          int x = 1;
          return fun1(x);
        }
        return null;
    }
}
