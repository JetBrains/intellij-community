class A {
}

class B {
}

public class Cce {
   public void a() {
      Object o = getObject();

      if (o instanceof A) {
        B b = (B) o;
      }
   }

   Object getObject() {
     return new A();
   }
}