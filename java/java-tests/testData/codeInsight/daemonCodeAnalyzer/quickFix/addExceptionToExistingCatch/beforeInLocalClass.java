// "Add exception to existing catch clause" "false"
import java.io.FileInputStream;

class Test {
  void m() {
    try {
      class A implements Runnable {
        @Override
        public void run() { }

        InputStream in = new <caret>FileInputStream("");
      };
    } catch (RuntimeException e) {
      e.printStackTrace();
    }
  }
}