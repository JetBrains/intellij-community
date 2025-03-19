// "Remove local variable 'oo'" "true-preview"
import java.io.*;

class a {
    int k;
    private int run() {
      Object <caret>i = null;

      return 0;
    }
}

