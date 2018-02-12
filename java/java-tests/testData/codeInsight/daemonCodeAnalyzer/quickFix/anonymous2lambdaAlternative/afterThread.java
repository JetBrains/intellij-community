// "Replace with new Thread(() -> {…})" "true"
public class Main {
  public void testThread() {
      // Comment outside
// Ending comment
      new Thread(() -> {
        // Comment inside
        System.out.println("Hello from thread!");
      }).start();
  }
}