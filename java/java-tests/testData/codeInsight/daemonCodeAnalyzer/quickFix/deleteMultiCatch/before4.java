// "Delete catch for 'java.lang.RuntimeException'" "true"
import java.io.*;

class C {
  void m() {
    try {
      int p = 0;
    }
    catch (<caret>RuntimeException | Exception e) {
    }
  }
}
