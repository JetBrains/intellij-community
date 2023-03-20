public class A {
   /**
    * @param p parameter description
    * @param t invalid parameter description
    */
   void foo(int p, int q) {}
}

class B extends A {
    /**
     * <caret>
     * @param p parameter description
     * @param q
     */
   void foo(int p, int q){}
}