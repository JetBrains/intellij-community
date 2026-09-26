<error descr="Cyclic inheritance involving 'A'">interface A extends C</error> {
  void foo();
}
<error descr="Cyclic inheritance involving 'B'">interface B extends A</error> {
  void foo();
}
<error descr="Cyclic inheritance involving 'C'">interface C extends B</error> {
  void foo();
}
<error descr="Cyclic inheritance involving 'C'">class D implements C</error> {
  public void foo() {}
}