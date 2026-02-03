// "Add Javadoc" "true-preview"

//Method Comments Algorithm
//
//  If a method does not have a documentation comment, or has an {@inheritDoc} tag, then the standard doclet uses Automatic Supertype Search.

interface ClazzInterface1Interface2 {
  /**
   * @param h must be copied to 8th place. Just
   *          a few unicode symbols: &lt;&quest;&gt;
   * @param f blah blah blah
   * @param c blah blah blah
   * @param d blah blah blah
   * @param g blah blah blah
   */
  void foo(int a, int b, int c, int d, int e, int f, int g, int h, int i, int j);
}

interface ClazzInterface1Interface1 {
  /**
   * @param dd blah blah blah
   * @param ff blah blah blah
   * @param cc blah blah blah
   * @param gg must be copied to 7th place
   */
  void foo(int aa, int bb, int cc, int dd, int ee, int ff, int gg, int hh, int ii, int jj);
}

class ClazzClazz {
  /**
   * @param fff must be copied to 6th place
   * @param ccc blah blah blah
   */
  void foo(int aaa, int bbb, int ccc, int ddd, int eee, int fff, int ggg, int hhh, int iii, int jjj) {
  }
}

interface ClazzInterface1 extends ClazzInterface1Interface1, ClazzInterface1Interface2 {
  /**
   * @param ffff blah blah blah
   * @param dddd must be copied to 4th place
   * @param cccc blah blah blah
   */
  void foo(int aaaa, int bbbb, int cccc, int dddd, int eeee, int ffff, int gggg, int hhhh, int iiii, int jjjj);
}

interface ClazzInterface2 {
  /**
   * @param ddddd blah blah blah
   * @param hhhhh blah blah blah
   * @param fffff blah blah blah
   * @param ccccc blah blah blah
   * @param ggggg blah blah blah
   * @param eeeee must be copied to 5th place
   */
  void foo(int aaaaa, int bbbbb, int ccccc, int ddddd, int eeeee, int fffff, int ggggg, int hhhhh, int iiiii, int jjjjj);
}

class Clazz extends ClazzClazz implements ClazzInterface1, ClazzInterface2 {
  /**
   * @param cccccc must be copied to 3rd place
   */
  public void foo(int aaaaaa, int bbbbbb, int cccccc, int dddddd, int eeeeee, int ffffff, int gggggg, int hhhhhh, int iiiiii, int jjjjjj) {
  }
}

interface Interface1Interface1 {
  /**
   * @param x1 blah blah blah
   * @param x3 blah blah blah
   * @param x4 blah blah blah
   * @param x5 blah blah blah
   * @param x6 blah blah blah
   * @param x7 blah blah blah
   * @param x8 blah blah blah
   * @param x10 must be copied to 10th place
   */
  void foo(int x1, int x2, int x3, int x4, int x5, int x6, int x7, int x8, int x9, int x10);
}

interface Interface1Interface2 {
  /**
   * @param b1 blah blah blah
   * @param b3 blah blah blah
   * @param b4 blah blah blah
   * @param b5 blah blah blah
   * @param b6 blah blah blah
   * @param b7 blah blah blah
   * @param b8 blah blah blah
   * @param b9  must be copied to 9th place
   * @param b10 blah blah blah
   */
  void foo(int b1, int b2, int b3, int b4, int b5, int b6, int b7, int b8, int b9, int b10);
}

interface Interface1 extends Interface1Interface1, Interface1Interface2 {
  /**
   * @param parameter1 must be copied to 1st place
   * @param parameter3 blah blah blah
   * @param parameter4 blah blah blah
   * @param parameter5 blah blah blah
   * @param parameter6 blah blah blah
   * @param parameter7 blah blah blah
   * @param parameter8 blah blah blah
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
   * @param param1 blah blah blah
   * @param param2 must be copied to 2nd place
   * @param param3 blah blah blah
   * @param param4 blah blah blah
   * @param param5 blah blah blah
   * @param param6 blah blah blah
   * @param param7 blah blah blah
   * @param param8 blah blah blah
   * @param param9 blah blah blah
   * @param param10 blah blah blah
   */
  void foo(int param1, int param2, int param3, int param4, int param5, int param6, int param7, int param8, int param9, int param10);
}

class Test extends Clazz implements Interface1, Interface2 {
  public void foo<caret>(int p1, int p2, int p3, int p4, int p5, int p6, int p7, int p8, int p9, int p10) {
  }
}