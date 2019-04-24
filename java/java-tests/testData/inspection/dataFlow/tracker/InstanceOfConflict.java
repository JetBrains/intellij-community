/*
Value is always false (s instanceof Integer)
  Type of 's' is exactly String (s)
    Type of 's' is known from line #10 (s instanceof String)
 */
import java.util.List;

class Test {
  void test(Object s) {
    if (s instanceof String) {
      if (<selection>s instanceof Integer</selection>) {}
    }
  }
}