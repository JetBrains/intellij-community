// "Make 'bar' return 'Foo'" "true"
public class Foo {
  public Foo(int i) {}

  Foo bar() {
    return new Foo("");
  }
}
