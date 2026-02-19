// "Replace with lambda" "true-preview"
class Test {
  Integer s;
  private void m() {
    Runnable r = new Runna<caret>ble() {
      @Override
      public void run() {
        System.out.println(s);
        Integer s = Test.this.s;
      }
    };
  }
}