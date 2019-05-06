/*
Value is always false (s == null)
  's' was assigned (=)
    's1' is known to be 'non-null' from line #8 (s1 == null)
 */
class Test {
  void test(String s, String s1) {
    if (s1 == null) return;
    s = s1;
    if (<selection>s == null</selection>) {
      
    }
  }
}