// "Replace explicit type with 'var'" "true"
class MyTest {
  private void m() {
    Ru<caret>nnable r = new Runnable() {
      @Override
      public void run() {}
    };
  }
}