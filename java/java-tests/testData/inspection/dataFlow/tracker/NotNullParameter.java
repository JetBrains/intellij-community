/*
Value is always false (null == s)
  Parameter 's' is annotated as 'non-null' (@NotNull String s)
 */
import org.jetbrains.annotations.NotNull;

class Test {
  void test(@NotNull String s) {
    if (<selection>null == s</selection>) return;
  }
}