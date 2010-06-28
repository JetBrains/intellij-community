// "Remove variable 'oo'" "true"
import java.io.*;

class a {
    int k;
    private int run() {
      <caret>Object i = null;

      return 0;
    }
}

