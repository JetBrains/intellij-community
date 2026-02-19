class A {
  public void £() {
  }
}

class B extends A {
  public void c() {
    <selection>super</selection>.£(); //select `super` and extract variable
  }
}