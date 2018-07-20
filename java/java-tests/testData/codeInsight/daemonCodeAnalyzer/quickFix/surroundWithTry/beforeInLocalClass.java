// "Surround with try/catch" "false"
// "Add exception to method signature" "false"
import java.io.*;

class C {
  public void m() {
    class Local {
      InputStream in = new File<caret>InputStream("");
    }
  }
}
