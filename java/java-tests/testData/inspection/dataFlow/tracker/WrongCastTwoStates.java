/*
Cast may fail ((Integer)x; line#12)
  An execution might exist where:
    An object type is exactly String which is not a subtype of Integer (x; line#12)
      Type of 'x' is known from line #10 (x instanceof String; line#10)
 */

class Test {
  void test(Object x) {
    if (x instanceof String) {
    }
    System.out.println(<selection>(Integer)x</selection>);
  }
}