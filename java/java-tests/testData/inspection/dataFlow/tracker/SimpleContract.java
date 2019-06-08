/*
Value is always false (Objects.isNull(s); line#12)
  According to inferred contract, method 'isNull' returns 'false' value when parameter != null (isNull; line#12)
    's' was dereferenced (s; line#11)
 */

import java.util.Objects;

class Test {
  void test(String s) {
    System.out.println(s.trim());
    if (<selection>Objects.isNull(s)</selection>) {
      
    }
  }
}