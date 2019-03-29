/*
Value is always true (b)
  'b == true' was established from condition (!b)
 */
import org.jetbrains.annotations.NotNull;

class Test {
  void test(boolean b) {
    if (!b) return;
    
    if(<selection>b</selection>) {}
  }
}