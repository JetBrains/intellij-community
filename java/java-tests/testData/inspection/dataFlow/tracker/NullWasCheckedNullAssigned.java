/*
Value is always false (s == s1)
  's' is known to be 'non-null' from line #8 (null == s)
  and 's1' was assigned (=)
 */
class Test {
  void test(String s) {
    if (null == s) return;
    String s1 = null;
    if (<selection>s == s1</selection>) {

    }
  }
}