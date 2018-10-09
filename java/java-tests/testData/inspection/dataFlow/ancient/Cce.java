class A {
}

class B {
}

public class Cce {
   public void a() {
      Object o = getObject();

      if (o instanceof A) {
        B b = (<warning descr="Casting 'o' to 'B' may produce 'ClassCastException'">B</warning>) o;
      }
   }

   Object getObject() {
     return new A();
   }
}