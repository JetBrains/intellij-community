/*
Value is always false (s == null)
  's' was assigned (=)
    Condition 's1 != null' was checked before (s1 == null)
 */
class Test {
  void test(String s, String s1) {
    if (s1 == null) return;
    s = s1;
    if (<selection>s == null</selection>) {
      
    }
  }
}