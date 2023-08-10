class A {
  public int foo(A a) {
    return 0;
  }

  public static void main(String[] args) {
    A a1 = new A();
    int a = a1.f<caret>oo(new A() {
      public int foo(A a) { return 1; }
    });
  }
}