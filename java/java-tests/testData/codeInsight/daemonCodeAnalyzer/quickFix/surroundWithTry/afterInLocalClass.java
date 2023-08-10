// "Surround with try/catch" "true-preview"
import java.io.*;

class C {
  public void m() {
    class Local {
      InputStream in;

        {
            try {
                in = new FileInputStream("");
            } catch (FileNotFoundException e) {
                throw new RuntimeException(e);
            }
        }
    }
  }
}
