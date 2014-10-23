// "Add constructor parameter" "true"
abstract class FooBar {
  protected final String myFoo;

  public FooBar(String myFoo) {
      this.myFoo = myFoo;
  }

  public FooBar(Integer interestingType, String myFoo) {
    this(myFoo);
  }

  public FooBar(int i, String myFoo) {
    this(myFoo);
  }
}