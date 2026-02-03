// "Add constructor parameter" "true"
class Foo {
  private final String text;

  public Foo(String text) {
    this.text = text;
  }

  public Foo(int i, String text) {
      this.text = text;
  }
}