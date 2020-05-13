package i18n;
public class Foo {
  public Foo(String s) {
  }

  {
    new Foo(<warning descr="Hardcoded string literal: \"abc de\"">"abc de"</warning>);
  }
}