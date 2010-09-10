import org.jetbrains.annotations.*;

class Test {
  void foo(String s) {
    s.substring(0);
  }

  /**
   * @param str
   */
  void bar(String str) {
    if (str.substring(0) == null) {
    }
  }
}