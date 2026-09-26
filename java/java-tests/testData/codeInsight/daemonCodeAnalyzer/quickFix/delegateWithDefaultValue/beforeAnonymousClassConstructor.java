// "Generate overloaded constructor with default parameter values" "false"
class Bar {
  interface Foo {
    void bar();
  }

  public void bar() {
    Foo foo = new Foo() {
      Foo(String str<caret>) {
      }

      @Override
      public void bar() { }
    };
  }
}