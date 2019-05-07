/*
Value is always true (s.length > 0; line#10)
  Left operand is >= 1 (s.length; line#10)
    Range is known from line #8 (s[0]; line#8)
 */
class Test {
  void test(String[] s) {
    if (s[0].isEmpty()) return;
    
    if (<selection>s.length > 0</selection>) {
      
    }
  }
}