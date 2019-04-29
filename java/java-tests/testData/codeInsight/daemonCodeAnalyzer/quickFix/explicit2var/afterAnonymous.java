// "Replace explicit type with 'var'" "true"
class MyTest {
  private void m() {
      var r = new Runnable() {
      @Override
      public void run() {}
    };
  }
}