// "Bind constructor parameters to fields" "false"

class A {
  protected String myS;
  public A(String s) {
    this.myS = s;
  }
}

class B extends A {
  public <caret>B(String s) {
    super(s);
  }
}

class C {
  public int foo(B b) {
    return b.myS.hashCode();
  }
}
