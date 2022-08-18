class MyTest {
  {
    new Runnable() {
      private static void print() {}
      @Override
      public void run() {
        Runnable r = () -> print();
      }
    }
  }
}