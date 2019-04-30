/*
Value is always false (x == null)
  One of the following happens:
    'x' was assigned (=)
      Expression cannot be null as it's newly created object (new Object())
    or 'x' was assigned (=)
      Expression cannot be null as it's literal ("foo")
 */

class Test {
  void test(boolean b) {
    Object x;
    if (b) {
      x = new Object();
    } else {
      x = "foo";
    }
    if (<selection>x == null</selection>) {
      
    }
  }
}