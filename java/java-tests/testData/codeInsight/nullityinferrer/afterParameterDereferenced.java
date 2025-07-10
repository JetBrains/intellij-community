import org.jetbrains.annotations.NotNull;

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

  /**
   * @param str
   */
  void bar(@NotNull String str) {
    if ((str).substring(0) == null) {
    }
  }
}