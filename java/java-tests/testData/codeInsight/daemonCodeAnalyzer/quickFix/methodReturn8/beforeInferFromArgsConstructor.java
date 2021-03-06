// "Make 'bar' return 'Foo'" "false"
public class Foo {
  public Foo(int i) {}

  String bar() {
    return new Foo<caret>("");
  }
}
