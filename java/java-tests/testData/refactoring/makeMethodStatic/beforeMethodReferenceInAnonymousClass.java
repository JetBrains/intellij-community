class MyTest {
  {
    new Runnable() {
      private void pri<caret>nt() {}
      @Override
      public void run() {
        Runnable r = this::print;
      }
    }
  }
}