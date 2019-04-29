/*
Cast may fail ((Integer)x)
  An object type is exactly String which is not a subtype of Integer (x)
    Type of 'x' is known from line #9 (x instanceof String)
 */

class Test {
  void test(Object x) {
    if (x instanceof String) {
      System.out.println(<selection>(Integer)x</selection>);
    }
  }
}