interface A {
  void <caret>m();
}

abstract class B extends A {
  public void m() {
    //do something
  }
}

class C extends B implements A {}