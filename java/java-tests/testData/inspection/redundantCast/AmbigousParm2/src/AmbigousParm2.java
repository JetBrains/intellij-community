interface A {}

interface B {}

class C implements A, B {}

public class AmbigousParameter {
   void m(A a) {}
   void m(B b) {}

   public void caller(C c) {
     m((A)c);
     m((A)null);
     A a = (A)c;
     m((A)a);
   }
}