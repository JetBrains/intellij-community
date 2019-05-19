/*
Value is always true (s != null; line#10)
  's' was assigned (=; line#9)
    Expression cannot be null as it's newly created object (new Object(); line#9)
 */

class Test {
  void test() {
    Object s = (new Object());
    if (<selection>s != null</selection>) {}
  }
}