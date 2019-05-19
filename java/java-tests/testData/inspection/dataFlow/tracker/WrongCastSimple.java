/*
Cast may fail ((Integer)x; line#10)
  An object type is exactly String which is not a subtype of Integer (x; line#10)
    Type of 'x' is known from line #9 (x instanceof String; line#9)
 */

class Test {
  void test(Object x) {
    if (x instanceof String) {
      System.out.println(<selection>(Integer)x</selection>);
    }
  }
}