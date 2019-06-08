/*
Value is always false (x; line#12)
  'x == false' was established from condition (x; line#9)
 */

class Test {
  void test(boolean x) {
    if(x) {}
    if(x) {
      
    } else {
      if(<selection>x</selection>) {}
    }
  }
}