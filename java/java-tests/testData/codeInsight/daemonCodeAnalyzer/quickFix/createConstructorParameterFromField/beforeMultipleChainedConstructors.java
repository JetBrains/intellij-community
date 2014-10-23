// "Add constructor parameter" "true"
abstract class FooBar {
  protected final String my<caret>Foo;

  public FooBar() {
  }

  public FooBar(Integer interestingType) {
    this();
  }

  public FooBar(int i) {
    this();
  }
}