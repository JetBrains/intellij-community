class MyTest {
  private void start() {
    interface ValueGetter {
      int va<caret>lue();
    }

    record Value(int value) implements ValueGetter{}
    
    class X implements ValueGetter {
      public int value() {return 0;}
    }
  }
}