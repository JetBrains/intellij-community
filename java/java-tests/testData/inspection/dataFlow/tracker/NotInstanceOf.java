/*
Value is always false (s instanceof String; line#12)
  An object is known to be not CharSequence which is a supertype of String (s; line#12)
    Type of 's' is known from line #10 (s instanceof CharSequence; line#10)
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