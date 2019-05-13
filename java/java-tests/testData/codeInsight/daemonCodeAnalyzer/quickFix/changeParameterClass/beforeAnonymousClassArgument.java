// "Make 'null' implement 'Foo.IBar'" "false"

public abstract class Foo {
  static Foo anonymous = new Foo() {
    @Override
    void fooMethod() {
      foo2Method(th<caret>is);
    }
  };

  protected Foo() {
    IBar bar = anonymous;
  }
}