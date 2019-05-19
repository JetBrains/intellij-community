/*
Value is always false (s == s1; line#10)
  's' is known to be 'non-null' from line #8 (null == s; line#8)
  and 's1' was assigned to 'null' (=; line#9)
 */
class Test {
  void test(String s) {
    if (null == s) return;
    String s1 = null;
    if (<selection>s == s1</selection>) {

    }
  }
}