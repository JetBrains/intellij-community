class CommonParent {
  void foo() {}
}

public class A extends CommonParent {

  private void <caret>f() {
    foo();
  }

  private class B extends CommonParent {
    void g() {
      foo();
    }
  }
}