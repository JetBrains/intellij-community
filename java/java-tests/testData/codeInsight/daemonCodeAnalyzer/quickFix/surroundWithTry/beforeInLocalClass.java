// "Surround with try/catch" "true-preview"
import java.io.*;

class C {
  public void m() {
    class Local {
      InputStream in = new File<caret>InputStream("");
    }
  }
}
