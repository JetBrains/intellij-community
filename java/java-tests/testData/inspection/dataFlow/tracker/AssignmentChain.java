/*
Value is always false (s == null; line#10)
  's' was assigned (=; line#9)
    's1' is known to be 'non-null' from line #8 (s1 == null; line#8)
 */
class Test {
  void test(String s, String s1) {
    if (s1 == null) return;
    s = s1;
    if (<selection>s == null</selection>) {
      
    }
  }
}