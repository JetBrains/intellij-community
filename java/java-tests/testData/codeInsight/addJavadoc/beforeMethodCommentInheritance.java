// "Add Javadoc" "true"

//Method Comments Algorithm
//
//  If a method does not have a documentation comment, or has an {@inheritDoc} tag, then the standard doclet uses the following algorithm to
//  search for an applicable comment. The algorithm is designed to find the most specific applicable documentation comment, and to give
//  preference to interfaces over superclasses:
//
//  1. Look in each directly implemented (or extended) interface in the order they appear following the word implements (or extends) in the
//  type declaration. Use the first documentation comment found for this method.
//  2. If Step 1 failed to find a documentation comment, then recursively apply this entire algorithm to each directly implemented (or
//  extended) interface in the same order they were examined in Step 1.
//  3. When Step 2 fails to find a documentation comment and this is a class other than the Object class, but not an interface:
//    a. If the superclass has a documentation comment for this method, then use it.
//    b. If Step 3a failed to find a documentation comment, then recursively apply this entire algorithm to the superclass.
//

interface ClazzInterface1Interface2 {
  /**
   * @param a blah blah blah
   * @param h must be copied to 8th place. Just
   *          a few unicode symbols: &lt;&quest;&gt;
   * @param b blah blah blah
   * @param c blah blah blah
   * @param d blah blah blah
   * @param g blah blah blah
   */
  void foo(int a, int b, int c, int d, int e, int f, int g, int h, int i, int j);
}

interface ClazzInterface1Interface1 {
  /**
   * @param bb blah blah blah
   * @param dd blah blah blah
   * @param aa blah blah blah
   * @param cc blah blah blah
   * @param gg must be copied to 7th place
   * @param ee blah blah blah
   */
  void foo(int aa, int bb, int cc, int dd, int ee, int ff, int gg, int hh, int ii, int jj);
}

class ClazzClazz {
  /**
   * @param hhh blah blah blah
   * @param aaa blah blah blah
   * @param bbb blah blah blah
   * @param ddd blah blah blah
   * @param fff must be copied to 6th place
   * @param eee blah blah blah
   * @param ccc blah blah blah
   * @param ggg blah blah blah
   */
  void foo(int aaa, int bbb, int ccc, int ddd, int eee, int fff, int ggg, int hhh, int iii, int jjj) {
  }
}

interface ClazzInterface1 extends ClazzInterface1Interface1, ClazzInterface1Interface2 {
  /**
   * @param dddd must be copied to 4th place
   * @param aaaa blah blah blah
   * @param bbbb blah blah blah
   * @param cccc blah blah blah
   */
  void foo(int aaaa, int bbbb, int cccc, int dddd, int eeee, int ffff, int gggg, int hhhh, int iiii, int jjjj);
}

interface ClazzInterface2 {
  /**
   * @param ddddd blah blah blah
   * @param bbbbb blah blah blah
   * @param ccccc blah blah blah
   * @param aaaaa blah blah blah
   * @param eeeee must be copied to 5th place
   */
  void foo(int aaaaa, int bbbbb, int ccccc, int ddddd, int eeeee, int fffff, int ggggg, int hhhhh, int iiiii, int jjjjj);
}

class Clazz extends ClazzClazz implements ClazzInterface1, ClazzInterface2 {
  /**
   * @param aaaaaa blah blah blah
   * @param bbbbbb blah blah blah
   * @param cccccc must be copied to 3rd place
   */
  public void foo(int aaaaaa, int bbbbbb, int cccccc, int dddddd, int eeeeee, int ffffff, int gggggg, int hhhhhh, int iiiiii, int jjjjjj) {
  }
}

interface Interface1Interface1 {
  /**
   * @param x10 must be copied to 10th place
   */
  void foo(int x1, int x2, int x3, int x4, int x5, int x6, int x7, int x8, int x9, int x10);
}

interface Interface1Interface2 {
  /**
   * @param b9  must be copied to 9th place
   * @param b10 blah blah blah
   */
  void foo(int b1, int b2, int b3, int b4, int b5, int b6, int b7, int b8, int b9, int b10);
}

interface Interface1 extends Interface1Interface1, Interface1Interface2 {
  /**
   * @param parameter1 must be copied to 1st place
   */
  void foo(int parameter1,
           int parameter2,
           int parameter3,
           int parameter4,
           int parameter5,
           int parameter6,
           int parameter7,
           int parameter8,
           int parameter9,
           int parameter10);
}

interface Interface2 {
  /**
   * @param param2 must be copied to 2nd place
   * @param param1 blah blah blah
   */
  void foo(int param1, int param2, int param3, int param4, int param5, int param6, int param7, int param8, int param9, int param10);
}

class Test extends Clazz implements Interface1, Interface2 {
  public void foo<caret>(int p1, int p2, int p3, int p4, int p5, int p6, int p7, int p8, int p9, int p10) {
  }
}