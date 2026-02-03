/*
Value is always false (s instanceof String; line#10)
  Value 's' is always 'null' (s; line#10)
    's == null' was established from condition (s == null; line#10)
 */
import java.util.List;

class Test {
  void test(Object s) {
    if (s == null && <selection>s instanceof String</selection>) {}
  }
}