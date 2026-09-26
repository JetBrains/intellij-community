// "Delete catch for 'java.io.FileNotFoundException'" "true-preview"
import java.io.*;

class C {
  void m() {
    try {
      int p = 0;
    }
    catch (EOFException | <caret>FileNotFoundException | RuntimeException e) {
    }
  }
}
