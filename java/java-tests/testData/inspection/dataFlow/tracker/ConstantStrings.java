/*
Value is always false (s == null; line#11)
  Result of 's != null' is known from line #10 (!"foo".equals(s) && !"bar".equals(s); line#10)
 */

import java.util.List;

class Test {
  void test(String s) {
    if (!"foo".equals(s) && !"bar".equals(s)) return;
    if (<selection>s == null</selection>) {}
  }
}