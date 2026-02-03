import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class NullFP {
  void test(@NotNull Y y) {}

  interface X {
    @Contract(pure = true)
    @Nullable Y getY();
  }

  interface Y {}
  interface Z extends Y {}

  void run(@NotNull final X x) {
    final Y y = x.getY();
    if (y instanceof Z) {}
    test(<warning descr="Argument 'x.getY()' might be null">x.getY()</warning>);
  }
}
