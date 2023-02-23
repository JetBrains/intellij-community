// "Reuse previous variable 'x' declaration" "true-preview"
import java.io.*;

class X {
  public void demo() {
    final boolean x;
    if (true) {
      x = false;
    } else {
        x = true;
    }
  }
}

