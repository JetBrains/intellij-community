/*
Value is always true (res == a; line#11)
  'res' was assigned (=; line#10)
    According to hard-coded contract, method 'max' returns 'param1' value when param1 > param2 (max; line#10)
      Condition 'a > b' was checked before (a > b; line#9)
 */
class X {
  void test(int a, int b) {
    if (a > b) {
      int res = Math.max(a, b);
      if (<selection>res == a</selection>) {
        
      }
    }
  }
}