/*
Value is always true (s instanceof String)
  An object is already known to be String (s)
    Type of 's' is known from line #10 ((String)s)
 */
import java.util.List;

class Test {
  void test(Object s) {
    System.out.println(((String)s).trim());
    
    
    if (<selection>s instanceof String</selection>) {
    }
  }
}