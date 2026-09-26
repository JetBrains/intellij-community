// "Replace with lambda" "true-preview"
class Test {
  {
    Runnable    r = b ? new Runna<caret>ble() {
      @Override
      public void run() {
      }
    } : null;
  }
}