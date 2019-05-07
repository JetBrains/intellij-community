/*
Cast may fail ((Integer)x; line#16)
  An execution might exist where:
    An object type is exactly Double which is not a subtype of Integer (x; line#16)
      Type of 'x' is known from line #14 (x instanceof Double; line#14)
    or an object type is exactly String which is not a subtype of Integer (x; line#16)
      Type of 'x' is known from line #12 (x instanceof String; line#12)
 */

class Test {
  void test(Object x) {
    if (x instanceof String) {
    }
    if (x instanceof Double) {
    }
    System.out.println(<selection>(Integer)x</selection>);
  }
}