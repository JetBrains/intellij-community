/*
Value is always false (s == null; line#11)
  's' is known to be 'non-null' from line #10 (!"foo".equals(s) && !"bar".equals(s); line#10)
 */

import java.util.List;

class Test {
  void test(String s) {
    if (!"foo".equals(s) && !"bar".equals(s)) return;
    if (<selection>s == null</selection>) {}
  }
}