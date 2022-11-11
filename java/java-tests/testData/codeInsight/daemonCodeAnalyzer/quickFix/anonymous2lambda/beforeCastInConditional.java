// "Replace with lambda" "true-preview"
class Test {
  {
    Object    r = b ? new Runna<caret>ble() {
      @Override
      public void run() {
      }
    } : null;
  }
}