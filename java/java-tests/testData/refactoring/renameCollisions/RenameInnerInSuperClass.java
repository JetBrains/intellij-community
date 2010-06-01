class A {
  class <caret>B {
    int x = 42;
  }
}

public class D {
  static class C extends A {
    int x = 23;

    int m() {
      return C.this.x;
    }
  }

  public static void main(String[] args) {
    System.out.println(new C().m());
  }
}