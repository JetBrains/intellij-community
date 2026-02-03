import org.jetbrains.annotations.*;

class Test {
  void foo(@NotNull String s) {
  }

  void bar(@NotNull String str) {
    foo(str);
  }
}