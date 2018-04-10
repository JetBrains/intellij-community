// "Remove redundant initializer" "true"
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