// "Create local variable 'x'" "true"
class other {
  public int method1() { return 1;}
  public String field1;
}
class A {
    public void foo() {
        <caret>x.method1();
    }
}