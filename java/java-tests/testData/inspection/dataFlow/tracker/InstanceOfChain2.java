/*
Value is always true (s instanceof RandomAccess)
  An object is already known to be ArrayList which is a subtype of RandomAccess (s)
    Type of 's' is known from line #12 (s instanceof ArrayList)
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