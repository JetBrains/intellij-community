/*
Value is always true (list.add("foo"); line#10)
  According to contract, method 'add' always returns 'true' value (add; line#10)
 */

import java.util.List;

class Test {
  void test(List<String> list) {
    if(<selection>list.add("foo")</selection>) {}
  }
}