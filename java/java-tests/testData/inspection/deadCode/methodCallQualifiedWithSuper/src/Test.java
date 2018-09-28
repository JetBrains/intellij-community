class A {
  void m(String string) {

  }
}

class B extends A {
  void m(String string) {
    super.m(string + "///");
  }
}

public class Test {
  public static void main(String[] args) {
    new B().m("abc");
  }
}
