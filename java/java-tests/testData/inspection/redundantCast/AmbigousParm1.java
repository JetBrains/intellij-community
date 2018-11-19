interface A {}

interface B {}

class C implements A, B {}

class AmbigousParameter {
   public void ua(A a) {}

   public void caller(C c) {
     ua((<warning descr="Casting 'c' to 'A' is redundant">A</warning>)c);
   }
}