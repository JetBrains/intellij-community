public class MyClass {

    private Inner inner;

    public void test() {
        inner = new Inner();
        if (inner.isWriteReplace()) {
        return inner.writeReplace();
      }
      return false;
    }

  private class Inner {
      public boolean isWriteReplace() {
          return false;
      }
  
      public boolean writeReplace() {
          return false;
      }
  }
}
