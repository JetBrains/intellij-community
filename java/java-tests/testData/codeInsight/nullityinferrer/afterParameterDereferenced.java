import org.jetbrains.annotations.*;

class Test {
  void foo(@NotNull String s) {
    s.substring(0);
  }

  /**
   * @param str
   */
  void bar(@NotNull String str) {
    if (str.substring(0) == null) {
    }
  }
}