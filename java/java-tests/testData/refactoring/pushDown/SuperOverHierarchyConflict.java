class A {
  void k() {
    System.out.println(23);
  }
}

class B extends A {
  void k() {
    System.out.println(42);
  }

  void <caret>m() {
    super.k();
  }
}

public class C extends B {
  public static void main(String[] args) {
    new C().m();
  }
}