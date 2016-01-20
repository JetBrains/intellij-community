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
    class Local {
      void s(@Nullable String s) {
        final int i = s.<warning descr="Method invocation 'hashCode' may produce 'java.lang.NullPointerException'">hashCode</warning>();
      }
    }
  }
}