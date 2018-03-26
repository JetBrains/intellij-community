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
    string = <warning descr="Assigning 'null' value to non-annotated field">null</warning>;
    super.bar();

    if (string != null)
      super.bar();
  }
}