/*
Value is always true (s instanceof String; line#13)
  An object is already known to be String (s; line#13)
    Type of 's' is known from line #10 ((String)s; line#10)
 */
import java.util.List;

class Test {
  void test(Object s) {
    System.out.println(((String)s).trim());
    
    
    if (<selection>s instanceof String</selection>) {
    }
  }
}