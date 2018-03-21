// "Add constructor parameter" "true"
class Foo {
  private final String te<caret>xt;

  public Foo(String text) {
    this.text = text;
  }

  public Foo(int i) {
  }
}