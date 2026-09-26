class TestCase{
  public void main() {
    class T1 {
      public T1() {}
    }
    fo<caret>o();
  }

  public void foo() {
    class T1 {
      T1 t;
      public T1() {}
    }
    class T2 {
      T2 t;
      public T2() {}
    }
  }
}