class Foo {
  public String someMethod1() {
    return null;
  }

  public String someMethod2(String s) {
    return null;
  }

  public Runnable someMethod3() {
    return null;
  }

  void m() {
    someMethod1();
    someMethod1();
    someMethod1();

    someMethod2("");
    someMethod2("");
    someMethod2("");
    someMethod2("");

    someMethod3();
    someMethod3();
    someMethod3();
    someMethod3();
    someMethod3();
  }

  void mm(Foo f) {
    <caret>
  }

}