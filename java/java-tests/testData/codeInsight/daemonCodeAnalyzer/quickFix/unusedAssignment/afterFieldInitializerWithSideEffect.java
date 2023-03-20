// "Remove redundant initializer" "true-preview"
class A {
  private String myFoo;

    {
        abc();
    }

    protected String abc() {
    return "";
  }

  public A(String myFoo) {
    this.myFoo = myFoo;
  }
}