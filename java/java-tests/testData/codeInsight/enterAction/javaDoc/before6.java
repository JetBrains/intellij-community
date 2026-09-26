public class A {
   /**
    * @param p parameter description
    * @param t invalid parameter description
    */
   void foo(int p, int q) {}
}

class B extends A {
   /**<caret>
   void foo(int p, int q){}
}