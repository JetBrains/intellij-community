class A {
  static class <caret>B {
    static int x = 42;
  }
}

public class D {
  static class C extends A {
    static int x = 23;

    static int m() {
      return C.x;
    }
  }

  public static void main(String[] args) {
    System.out.println(C.m());
  }
}