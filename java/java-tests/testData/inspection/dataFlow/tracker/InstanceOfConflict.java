/*
Value is always false (s instanceof Integer; line#11)
  An object type is exactly String which is not a subtype of Integer (s; line#11)
    Type of 's' is known from line #10 (s instanceof String; line#10)
 */
import java.util.List;

class Test {
  void test(Object s) {
    if (s instanceof String) {
      if (<selection>s instanceof Integer</selection>) {}
    }
  }
}