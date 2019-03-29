/*
Value is always true (s.length > 0)
  Left operand range is {1..Integer.MAX_VALUE} (s.length)
    Range is known from here (s[0])
 */
class Test {
  void test(String[] s) {
    if (s[0].isEmpty()) return;
    
    if (<selection>s.length > 0</selection>) {
      
    }
  }
}