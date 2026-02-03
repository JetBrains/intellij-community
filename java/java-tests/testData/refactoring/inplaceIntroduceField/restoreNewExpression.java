public class MyClass {
    public void test() {
      if (new In<caret>ner().isWriteReplace()) {
        return new Inner().writeReplace();
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
