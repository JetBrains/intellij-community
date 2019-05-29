/*
Value is always false (null == s; line#9)
  Parameter 's' is annotated as 'non-null' (@NotNull; line#8)
 */
import org.jetbrains.annotations.NotNull;

class Test {
  void test(@NotNull String s) {
    if (<selection>null == s</selection>) return;
  }
}