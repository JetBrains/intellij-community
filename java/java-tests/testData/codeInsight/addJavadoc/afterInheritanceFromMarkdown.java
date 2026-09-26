// "Add Javadoc" "true-preview"
class A {
  /// @param a  Very beautiful param
  ///           from mk jdoc
  void test(int a) {}
}
class B extends A {
    /**
     * @param a Very beautiful param 
     *          from mk jdoc
     */
  void test(int a) {}
}