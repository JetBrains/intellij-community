// "Add exception to existing catch clause" "true"
import java.io.FileInputStream;
import java.io.FileNotFoundException;

class Test {
  void m() {
    try {
      new Runnable() {
        @Override
        public void run() { }

        InputStream in = new FileInputStream("");
      };
    } catch (RuntimeException | FileNotFoundException e) {
      e.printStackTrace();
    }
  }
}