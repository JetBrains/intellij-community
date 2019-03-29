/*
Value is always false (s == s1)
  's1' was assigned (null)
  s != null was checked before (null == s)
 */
class Test {
  void test(String s) {
    if (null == s) return;
    String s1 = null;
    if (<selection>s == s1</selection>) {

    }
  }
}