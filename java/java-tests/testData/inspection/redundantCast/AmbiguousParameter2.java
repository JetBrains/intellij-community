interface A {}

interface B {}

class C implements A, B {}

class AmbiguousParameter {
   void m(A a) {}
   void m(B b) {}

   public void caller(C c) {
     m((A)c);
     m((A)null);
     A a = (<warning descr="Casting 'c' to 'A' is redundant">A</warning>)c;
     m((<warning descr="Casting 'a' to 'A' is redundant">A</warning>)a);
   }
}