/*
Cast may fail ((Integer)x)
  An execution might exist where:
    An object type is exactly Double which is not a subtype of Integer (x)
      Type of 'x' is known from line #14 (x instanceof Double)
    or an object type is exactly String which is not a subtype of Integer (x)
      Type of 'x' is known from line #12 (x instanceof String)
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