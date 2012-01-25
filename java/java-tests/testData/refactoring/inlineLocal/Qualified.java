public class A {
  int f = 23;

  static class B {
    static int f = 42;
  }

  int foo() {
    int r = B.f;
    A B = this;
    return r<caret>;
  }

  public static void main(String[] args) {
    System.out.println(new A().foo());
  }
}