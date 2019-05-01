/*
Value is always false (s instanceof String)
  An object is known to be not CharSequence which is a supertype of String (s)
    Type of 's' is known from line #10 (s instanceof CharSequence)
 */
import java.util.List;

class Test {
  void test(Object s) {
    if (!(s instanceof CharSequence)) {
      if (s instanceof Integer) {
        if (<selection>s instanceof String</selection>){
        }
      }
    }
  }
}