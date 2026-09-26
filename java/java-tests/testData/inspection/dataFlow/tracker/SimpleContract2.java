/*
Value is always false (Objects.isNull(s.trim()); line#11)
  According to inferred contract, method 'isNull' returns 'false' when s.trim() != null (isNull; line#11)
    Method 'trim' is externally annotated as 'non-null' (trim; line#11)
 */

import java.util.Objects;

class Test {
  void test(String s) {
    if (<selection>Objects.isNull(s.trim())</selection>) {
      
    }
  }
}