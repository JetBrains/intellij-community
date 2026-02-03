class A {
  public A(String a) {
  }
}

class B extends A {
  public B(String a) {
    super(a);
  }
  public B(String a) {
    super(a);
    String <caret>b = "b";
  }
}