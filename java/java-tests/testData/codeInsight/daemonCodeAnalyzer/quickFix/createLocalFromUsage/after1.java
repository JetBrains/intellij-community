// "Create local variable 'x'" "true-preview"
class other {
  public int method1() { return 1;}
  public String field1;
}
class A {
    public void foo() {
        other x<caret>;
        x.method1();
    }
}