// "Remove redundant initializer" "true-preview"
class A {
  private String myFoo = ab<caret>c();

  protected String abc() {
    return "";
  }

  public A(String myFoo) {
    this.myFoo = myFoo;
  }
}