/*
Value is always false (s == null; line#11)
  It's known that 's != null' from line #10 (!"foo".equals(s) && !"bar".equals(s); line#10)
 */

import java.util.List;

class Test {
  void test(String s) {
    if (!"foo".equals(s) && !"bar".equals(s)) return;
    if (<selection>s == null</selection>) {}
  }
}