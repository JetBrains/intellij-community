class InstanceofThis {
  void test() {
    if(<warning descr="'instanceof' check for 'this'">this</warning> instanceof Child) {
      System.out.println("Child");
    }
    if(<warning descr="Class comparison for 'this'">this</warning>.getClass().equals(Child.class)) {
      System.out.println("Exactly child");
    }
    if(<warning descr="Class comparison for 'this'">getClass()</warning>.equals(Child.class)) {
      System.out.println("Exactly child");
    }
  }

  class Nested {
    void test() {
      if(InstanceofThis.this instanceof Child) {
        System.out.println("Outer is Child");
      }
      if(<warning descr="Class comparison for 'this'">getClass()</warning>.equals(Child.class)) {
        System.out.println("Exactly child");
      }
    }
  }
}

class Child extends InstanceofThis {}