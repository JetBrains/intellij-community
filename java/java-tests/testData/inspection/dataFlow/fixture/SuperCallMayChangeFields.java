class Base {
  void foo() {}
  void bar() { foo(); }
}

class Test extends Base {
  private String string;

  void foo() {
    string = "";
  }

  protected void bar() {
    string = null;
    super.bar();

    if (string != null)
      super.bar();
  }
}