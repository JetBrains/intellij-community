/*
Value is always false (s == null; line#9)
  's' is known to be 'non-null' from line #7 (null == s; line#7)
 */
class Test {
  void test(String s) {
    if (null == s) return;
    System.out.println(s.trim());
    if (<selection>s == null</selection>) {

    }
  }
}