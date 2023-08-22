// "Extract side effects as an 'if' statement" "true-preview"
import java.io.File;

public class Main {
  public void test(File f) {
      if (!f.isDirectory()) {
          f.mkdirs();
      }
  }
}