class MyTest {
  private void start() {
    interface ValueGetter {
      int newValue();
    }

    record Value(int newValue) implements ValueGetter{}
    
    class X implements ValueGetter {
      public int newValue() {return 0;}
    }
  }
}