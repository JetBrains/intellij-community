/*
Value is always true (null == s; line#10)
  's' was assigned to 'null' (=; line#9)
 */
import org.jetbrains.annotations.NotNull;

class Test {
  void test(@NotNull String s) {
    s = null;
    if (<selection>null == s</selection>) return;
  }
}