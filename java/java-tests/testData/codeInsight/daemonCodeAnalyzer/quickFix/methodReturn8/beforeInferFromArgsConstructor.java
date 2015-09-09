// "Make 'bar' return 'Foo'" "true"
public class Foo {
  public Foo(int i) {}

  String bar() {
    return new Foo<caret>("");
  }
}
