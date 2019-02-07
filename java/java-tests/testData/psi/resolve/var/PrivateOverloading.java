class A {
    int i;

    public void method() {
    }
  }

  class B extends A {
    private int i;

    public void method() {
    }
  }

  class C extends B {

    public void method() {
      <caret>i = 10;
    }
  }