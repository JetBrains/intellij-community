/*
Value is always false (s == null; line#8)
  's' was dereferenced (s; line#7)
 */
class Test {
  void test(String s) {
    System.out.println(s.trim());
    if (<selection>s == null</selection>) {

    }
  }
}