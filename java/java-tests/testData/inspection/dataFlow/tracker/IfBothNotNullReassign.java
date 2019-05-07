/*
Value is always false (y == null; line#20)
  'y' was assigned (=; line#19)
    One of the following happens:
      'x' was assigned (=; line#15)
        Expression cannot be null as it's newly created object (new Object(); line#15)
      or 'x' was assigned to '"foo"' (=; line#17)
        Expression cannot be null as it's literal ("foo"; line#17)
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