// "Surround with try/catch" "true"
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
