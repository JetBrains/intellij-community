interface A {}

interface B {}

class C implements A, B {}

public class AmbigousParameter {
   public void ua(A a) {}

   public void caller(C c) {
     ua((A)c);
   }
}