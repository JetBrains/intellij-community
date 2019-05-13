// "Replace with lambda" "true"
class Test {
  {
    Runnable    r = b ? new IRunna<caret>ble() {
      @Override
      public void run() {
      }
    } : null;
  }

  interface IRunnable extends Runnable {}
}