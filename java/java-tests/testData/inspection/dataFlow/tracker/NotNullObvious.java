/*
Value is always true (s != null)
  's' was assigned (=)
    Expression cannot be null as it's newly created object (new Object())
 */

class Test {
  void test() {
    Object s = (new Object());
    if (<selection>s != null</selection>) {}
  }
}