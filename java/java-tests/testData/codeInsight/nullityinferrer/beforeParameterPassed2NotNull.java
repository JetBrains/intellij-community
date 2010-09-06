import org.jetbrains.annotations.*;

class Test {
  void foo(@NotNull String s) {
  }

  void bar(String str) {
    foo(str);
  }
}