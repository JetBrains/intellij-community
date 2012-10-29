import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class Test {
  void f(@Nullable final Object x) {
    if (x != null) {
      class C {
        C(@NotNull Object x) {
        }

        C() {
          this(x);
        }
      }
    }
  }
}