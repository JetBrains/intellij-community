public class A {
  public int i;
}

class B extends A {
  public int getI() {
    return 42;
  }

  public static void main(String[] args) {
    A a = new B();
    a.i = 23;
    System.out.println(a.i);
  }
}
