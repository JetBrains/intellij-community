import java.util.function.*;
import typeUse.*;

interface Bug {
  static void test() {
    f(<warning descr="Method reference argument might be null">Bug::g</warning>);
  }

  static void f(@NotNull Consumer<@Nullable String> function) {
    function.accept(null);
  }

  static void g(@NotNull String s) {
    System.out.println(s.length());
  }
}