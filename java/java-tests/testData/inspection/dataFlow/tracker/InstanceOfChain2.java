/*
Value is always true (s instanceof RandomAccess; line#14)
  An object is already known to be ArrayList which is a subtype of RandomAccess (s; line#14)
    Type of 's' is known from line #12 (s instanceof ArrayList; line#12)
 */
import java.util.*;

class Test {
  void test(Object s) {
    if (s instanceof Map) {
      if (s instanceof List) {
        if (s instanceof ArrayList) {
          if (s instanceof CharSequence) {
            if (<selection>s instanceof RandomAccess</selection>){
            }
          }
        }
      }
    }
  }
}