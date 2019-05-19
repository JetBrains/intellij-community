/*
Value is always true (b; line#11)
  'b == true' was established from condition (!b; line#9)
 */
import org.jetbrains.annotations.NotNull;

class Test {
  void test(boolean b) {
    if (!b) return;
    
    if(<selection>b</selection>) {}
  }
}