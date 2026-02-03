// "Extract side effect" "false"
import java.io.File;

public class Main {
  public void test(File f) {
    (f.mkd<caret>irs());
  }
}