interface A {}
interface B {}

class AmbiguousParameter {
   public void f(A a) {}
   public void f(B b) {}
   public void f(Object o) {}

   public void g(Object o) {}

   public void caller() {
     f((A)null);
     g((<warning descr="Casting 'null' to 'A' is redundant">A</warning>)null);
   }
}