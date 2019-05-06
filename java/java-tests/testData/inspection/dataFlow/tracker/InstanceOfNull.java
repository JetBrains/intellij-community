/*
Value is always false (s instanceof String)
  Value 's' is always 'null' (s)
    's == null' was established from condition (s == null)
 */
import java.util.List;

class Test {
  void test(Object s) {
    if (s == null && <selection>s instanceof String</selection>) {}
  }
}