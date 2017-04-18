// "Replace with new Thread() with lambda argument" "false"
public class Main {
  public void testThread() {
    new <caret>Thread() {
      @Override
      public void run() {
        System.out.println("Hello from thread!");
        super.run();
      }
    }.start();
  }
}