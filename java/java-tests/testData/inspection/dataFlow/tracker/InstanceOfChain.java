/*
Value is always false (s instanceof String; line#12)
  An object is known to be Number which is definitely incompatible with String (s; line#12)
    Type of 's' is known from line #10 (s instanceof Number; line#10)
 */
import java.util.List;

class Test {
  void test(Object s) {
    if (s instanceof Number) {
      if (s instanceof Integer) {
        if (<selection>s instanceof String</selection>){
        }
      }
    }
  }
}