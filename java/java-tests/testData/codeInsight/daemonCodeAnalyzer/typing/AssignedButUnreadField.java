class Foo {
  @SuppressWarnings({"FieldCanBeLocal"})
  private final String text;
  public Foo(String text) {
    this.text = text;
  }
  public void doSomething(int i) {
    System.out.println(i+<caret>i);
  }
}
