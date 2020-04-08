// "Surround with try/catch" "true"
import java.io.*;

class C {
  public void m() {
    class Local {
      InputStream in = new File<caret>InputStream("");
    }
  }
}
