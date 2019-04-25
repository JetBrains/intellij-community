/*
Value is always true (s instanceof CharSequence)
  An object type is exactly String which is a subtype of CharSequence (s)
    Type of 's' is known from line #10 (s instanceof String)
 */
import java.util.List;

class Test {
  void test(Object s) {
    if (s instanceof String) {
      if (<selection>s instanceof CharSequence</selection>) {}
    }
  }
}