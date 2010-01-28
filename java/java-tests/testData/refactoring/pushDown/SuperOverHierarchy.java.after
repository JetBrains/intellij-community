class A {
  void k() {
    System.out.println(23);
  }
}

class B extends A {
  void k() {
    System.out.println(42);
  }

}

public class C extends B {
  public static void main(String[] args) {
    new C().m();
  }

    void m() {
      new C() {
        void foo() {
          super.k();
        }
      };
    }
}