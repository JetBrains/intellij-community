class A {
  public A(String a) {
  }
}

class B extends A {
  public B(String a) {
      this(a, null);
  }
  public B(String a, final String anObject) {
    super(a);
  }
}