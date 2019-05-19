/*
Value is always false (s == null; line#14)
  's' was assigned (=; line#13)
    One of the following happens:
      Expression cannot be null as it's literal ("foo"; line#13)
      or expression cannot be null as it's literal ("bar"; line#13)
 */

import java.util.List;

class Test {
  void test(boolean b) {
    String s = b ? "foo" : "bar";
    if (<selection>s == null</selection>) {}
  }
}