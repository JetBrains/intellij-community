/*
Call always fails (foo(s, "checked"); line#12)
  According to inferred contract, method 'foo' throws exception when obj == null (foo; line#12)
    Condition 's == null' was checked before (s == null; line#11)
 */

import java.util.Objects;

class Test {
  void test(String s) {
    if (s == null) {
      <selection>foo(s, "checked")</selection>;
    }
  }
  
  static void foo(Object obj, String message) {
    if(obj == null) {
      throw new RuntimeException(message);
    }
  }
}