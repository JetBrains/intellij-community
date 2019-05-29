/*
Value is always false (x == null; line#18)
  One of the following happens:
    'x' was assigned (=; line#14)
      Expression cannot be null as it's newly created object (new Object(); line#14)
    or 'x' was assigned to '"foo"' (=; line#16)
      Expression cannot be null as it's literal ("foo"; line#16)
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