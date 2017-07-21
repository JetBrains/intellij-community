class Foo {
  private abstract class A {
    public int getValue() {
      return 20173;
    }
  }

  private class B extends A {
    private B() {
      int a = this.getValue();
    }
  }
}