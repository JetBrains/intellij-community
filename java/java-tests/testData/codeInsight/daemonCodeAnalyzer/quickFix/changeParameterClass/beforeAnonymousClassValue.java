// "Make 'null' implement 'Foo.IBar'" "false"

public abstract class Foo {
  public static interface IBar {
    void barMethod();
  }

  abstract void fooMethod();

  void foo2Method(IBar b) {
  }

  static Foo anonymous = new Foo() {
    @Override
    void fooMethod() {
      foo2Method(th<caret>is);
    }
  };
}