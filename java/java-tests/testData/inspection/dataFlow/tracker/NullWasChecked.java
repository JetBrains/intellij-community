/*
Value is always false (s == null)
  's' is known to be 'non-null' from line #7 (null == s)
 */
class Test {
  void test(String s) {
    if (null == s) return;
    System.out.println(s.trim());
    if (<selection>s == null</selection>) {

    }
  }
}