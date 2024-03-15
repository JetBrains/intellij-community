class TestCase{
  public void main() {
    class T {
      public T() {}
    }
    /*]*/fo<caret>o();/*[*/
  }

  public void foo() {
    class T {
      T t;
      public T() {}
    }
  }
}