public class A {
  static int getI() {
    return 23;
  }

  static class B {
    static private int i = 42;

    static int m() {
      return getI();
    }
  }

  public static void main(String[] args) {
    System.out.println(B.m());
  }
}
