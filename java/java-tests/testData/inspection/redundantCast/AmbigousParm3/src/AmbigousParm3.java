interface A {}
interface B {}

public class AmbigousParameter {
   public void f(A a) {}
   public void f(B b) {}
   public void f(Object o) {}

   public void g(Object o) {}

   public void caller() {
     f((A)null);
     g((A)null);
   }
}