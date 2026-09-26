// "Add Javadoc" "true-preview"

class A {
  /**
   * @param a Very beautiful param
   *          From the classic javadoc
   */
  void test(int a) {}
}
class B extends A {
    /// @param a Very beautiful param 
    ///          From the classic javadoc
  void test(int a) {}
}