// "Add Javadoc" "true"

class A {
  /**
   * @param b    it's a description
   *             for the
   *             second param
   * @param a    it&apos;s &#97; description for the first param
   * @param blah it's a description
   *             for a nonexistent parameter
   * @param c    it's a description
   *             for the third param
   */
  void test(int a, int b, int c, int d) {}
}

class B extends A {
  @Override
  void test<caret>(final int x, int y, int z, int w) {}
}
