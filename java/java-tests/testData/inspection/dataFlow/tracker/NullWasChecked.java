/*
Value is always false (s == null)
  s != null was checked before (null == s)
 */
class Test {
  void test(String s) {
    if (null == s) return;
    System.out.println(s.trim());
    if (<selection>s == null</selection>) {

    }
  }
}