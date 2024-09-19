<fold text='/// outer class javadoc ...'>/// outer class javadoc
 /// javadoc body</fold>

class Test {

  <fold text='/// method javadoc ...'>/// method javadoc
   /// javadoc body
   ///
   /// @param i</fold>

  void foo(int i) <fold text='{...}'>{
    <fold text='/// method var javadoc ...'>/// method var javadoc
     /// javadoc body</fold>

    int j = i;
  }</fold>

  <fold text='/// first line ...'>/// first line
   /// second line</fold>

  void illFormedJavaDocMultilines() <fold text='{}'>{
  }</fold>

  <fold text='/// first line ...'>/// first line
   ///
   ///</fold>

  void javaDocWithTextOnlyOnFirstLine() <fold text='{}'>{

  }</fold>

  <fold text='/// second line ...'>/// second line
   ///</fold>

  void javaDocWithTextOnlyOnSecondLine() <fold text='{}'>{

  }</fold>

  <fold text='///  '>/// </fold>

  void emptyJavadoc() <fold text='{}'>{

  }</fold>

  <fold text='/// dangling javadoc ...'>/// dangling javadoc
   /// javadoc body</fold>


  <fold text='/// inner class javadoc ...'>/// inner class javadoc
   /// javadoc body</fold>

  class Inner <fold text='{...}'>{

    <fold text='/// javadoc for method in inner class ...'>/// javadoc for method in inner class
     /// javadoc body</fold>

    void foo() <fold text='{...}'>{
      <fold text='/// javadoc for class in method ...'>/// javadoc for class in method
       /// javadoc body</fold>

      class MethodInner <fold text='{...}'>{

        <fold text='/// javadoc for method inside class defined in method ...'>/// javadoc for method inside class defined in method
         /// javadoc body</fold>

        void bar() <fold text='{}'>{
        }</fold>
      }</fold>
    }</fold>
  }</fold>
}