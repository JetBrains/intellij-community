/*
Value is always true (null == s)
  's' was assigned (null)
 */
import org.jetbrains.annotations.NotNull;

class Test {
  void test(@NotNull String s) {
    s = null;
    if (<selection>null == s</selection>) return;
  }
}