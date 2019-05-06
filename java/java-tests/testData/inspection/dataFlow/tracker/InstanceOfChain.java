/*
Value is always false (s instanceof String)
  An object is known to be Number which is definitely incompatible with String (s)
    Type of 's' is known from line #10 (s instanceof Number)
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