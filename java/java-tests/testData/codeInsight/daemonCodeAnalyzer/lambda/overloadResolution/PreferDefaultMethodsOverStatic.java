class Test {
  interface I {
    default void f() {}
  }

  interface J {
    static void f() {}
  }

  static class IJ implements I, J {}
  static class JI implements J, I {}

  public static void main(String[] args) {
    new IJ(). f();
    new JI(). f();
    J.f();
  }
}