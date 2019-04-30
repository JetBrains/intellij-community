/*
Value is always false (y == null)
  'y' was assigned (=)
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
    Object y = x;
    if (<selection>y == null</selection>) {
      
    }
  }
}