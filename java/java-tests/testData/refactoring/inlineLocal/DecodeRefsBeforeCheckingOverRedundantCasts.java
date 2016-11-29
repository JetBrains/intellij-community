class Outer {

  public void bar() {
    Outer foo = this;

    new Object() {
      public void run() {
        f<caret>oo.baz();
      }
    };
  }

  public void baz() {
  }
}